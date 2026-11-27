'use client';

import React, { useEffect, useId, useRef, useState } from 'react';
import { Sparkles, X, Send, Loader2, Trash2, AlertTriangle, Bot, User as UserIcon } from 'lucide-react';
import { askNutritionAiAction } from '@/app/actions';
import type { AiChatMessage } from '@/lib/api';

interface NutritionAiAssistantProps {
  currentDate: string;
}

interface ChatEntry {
  id: string;
  role: 'user' | 'model';
  text: string;
}

const QUICK_SUGGESTIONS = [
  'O que comer agora para bater meus macros?',
  'Como está meu dia até agora?',
  'Sugira uma substituição para minha próxima refeição',
] as const;

/**
 * Renderizador Markdown enxuto e 100% seguro (sem dangerouslySetInnerHTML).
 * Suporta parágrafos, listas (-, *, 1.), subtítulos (###) e negrito (**texto**).
 */
function renderInlineMarkdown(text: string): React.ReactNode[] {
  const tokens = text.split(/(\*\*[^*]+\*\*|`[^`]+`|\*[^*\n]+\*)/g);
  return tokens.map((token, idx) => {
    if (token.startsWith('**') && token.endsWith('**') && token.length > 4) {
      return (
        <strong key={idx} className="font-bold text-white">
          {token.slice(2, -2)}
        </strong>
      );
    }
    if (token.startsWith('`') && token.endsWith('`') && token.length > 2) {
      return (
        <code key={idx} className="px-1 py-0.5 rounded bg-slate-900/80 text-emerald-300 text-[11px] font-mono">
          {token.slice(1, -1)}
        </code>
      );
    }
    if (
      token.startsWith('*') &&
      token.endsWith('*') &&
      token.length > 2 &&
      !token.startsWith('* ') &&
      !token.endsWith(' *')
    ) {
      return (
        <em key={idx} className="italic text-slate-100">
          {token.slice(1, -1)}
        </em>
      );
    }
    return <React.Fragment key={idx}>{token}</React.Fragment>;
  });
}

function FormattedMarkdown({ text }: { text: string }) {
  const lines = text.split(/\r?\n/);
  const blocks: React.ReactNode[] = [];
  let listType: 'ul' | 'ol' | null = null;
  let listItems: string[] = [];

  const flushList = (keyPrefix: string) => {
    if (!listType || listItems.length === 0) return;
    if (listType === 'ol') {
      blocks.push(
        <ol key={`${keyPrefix}-ol`} className="list-decimal pl-5 space-y-1 text-slate-200">
          {listItems.map((item, i) => (
            <li key={i} className="leading-relaxed">
              {renderInlineMarkdown(item)}
            </li>
          ))}
        </ol>
      );
    } else {
      blocks.push(
        <ul key={`${keyPrefix}-ul`} className="list-disc pl-4 space-y-1 text-slate-200">
          {listItems.map((item, i) => (
            <li key={i} className="leading-relaxed">
              {renderInlineMarkdown(item)}
            </li>
          ))}
        </ul>
      );
    }
    listType = null;
    listItems = [];
  };

  lines.forEach((rawLine, index) => {
    const line = rawLine.trim();
    if (!line) {
      flushList(`b-${index}`);
      return;
    }

    if (/^[-*_]{3,}$/.test(line)) {
      flushList(`hr-${index}`);
      blocks.push(<hr key={`hr-${index}`} className="border-slate-800 my-2" />);
      return;
    }

    const unorderedMatch = line.match(/^[-*•]\s+(.*)$/);
    if (unorderedMatch) {
      if (listType === 'ol') {
        flushList(`ul-switch-${index}`);
      }
      listType = 'ul';
      listItems.push(unorderedMatch[1]);
      return;
    }

    const orderedMatch = line.match(/^\d+[.)]\s+(.*)$/);
    if (orderedMatch) {
      if (listType === 'ul') {
        flushList(`ol-switch-${index}`);
      }
      listType = 'ol';
      listItems.push(orderedMatch[1]);
      return;
    }

    flushList(`p-${index}`);

    const headingMatch = line.match(/^#{1,4}\s+(.*)$/);
    if (headingMatch) {
      blocks.push(
        <p key={`h-${index}`} className="font-bold text-emerald-300 pt-1">
          {renderInlineMarkdown(headingMatch[1])}
        </p>
      );
      return;
    }

    const quoteMatch = line.match(/^>\s+(.*)$/);
    if (quoteMatch) {
      blocks.push(
        <blockquote key={`q-${index}`} className="border-l-2 border-emerald-500/50 pl-3 text-slate-300 italic leading-relaxed">
          {renderInlineMarkdown(quoteMatch[1])}
        </blockquote>
      );
      return;
    }

    blocks.push(
      <p key={`t-${index}`} className="leading-relaxed text-slate-200 whitespace-pre-wrap">
        {renderInlineMarkdown(line)}
      </p>
    );
  });

  flushList('end');

  return <div className="space-y-1.5 text-xs sm:text-[13px] break-words">{blocks}</div>;
}

export function NutritionAiAssistant({ currentDate }: NutritionAiAssistantProps) {
  const dialogRef = useRef<HTMLDialogElement>(null);
  const sheetRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLTextAreaElement>(null);
  const messagesEndRef = useRef<HTMLDivElement>(null);

  const dialogId = useId();
  const titleId = useId();

  const [isOpen, setIsOpen] = useState(false);
  const [messages, setMessages] = useState<ChatEntry[]>([]);
  const [input, setInput] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [errorBanner, setErrorBanner] = useState<{ status: number; message: string } | null>(null);
  const [activeModel, setActiveModel] = useState<string>('gemini-2.5-flash');

  const openDrawer = () => {
    const dialog = dialogRef.current;
    if (!dialog) return;
    if (!dialog.open) {
      dialog.showModal();
    }
    setIsOpen(true);
    requestAnimationFrame(() => {
      inputRef.current?.focus();
    });
  };

  const closeDrawer = () => {
    const dialog = dialogRef.current;
    if (dialog?.open) {
      dialog.close();
    }
    setIsOpen(false);
  };

  // Sincroniza estado ao fechar via Esc / gesto nativo e implementa fallback de light-dismiss
  useEffect(() => {
    const dialog = dialogRef.current;
    if (!dialog) return;

    const handleClose = () => {
      dialog.returnValue = '';
      setIsOpen(false);
    };
    dialog.addEventListener('close', handleClose);

    // Light-dismiss clicando fora do sheet (testa coordenadas do ponteiro para não fechar ao arrastar seleção de texto)
    const isOutsideSheet = (e: PointerEvent) => {
      const sheet = sheetRef.current;
      if (!sheet) return false;
      const r = sheet.getBoundingClientRect();
      return e.clientX < r.left || e.clientX > r.right || e.clientY < r.top || e.clientY > r.bottom;
    };

    let pressedOutside = false;
    const handlePointerDown = (e: PointerEvent) => {
      pressedOutside = isOutsideSheet(e);
    };
    const handlePointerUp = (e: PointerEvent) => {
      if (pressedOutside && isOutsideSheet(e)) {
        dialog.close();
      }
      pressedOutside = false;
    };

    dialog.addEventListener('pointerdown', handlePointerDown);
    dialog.addEventListener('pointerup', handlePointerUp);

    return () => {
      dialog.removeEventListener('close', handleClose);
      dialog.removeEventListener('pointerdown', handlePointerDown);
      dialog.removeEventListener('pointerup', handlePointerUp);
    };
  }, []);

  // Auto-scroll para última mensagem
  useEffect(() => {
    if (isOpen) {
      messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
    }
  }, [messages, isLoading, isOpen]);

  const sendMessage = async (rawText: string) => {
    const text = rawText.trim();
    if (!text || isLoading) return;

    setErrorBanner(null);

    const historyPayload: AiChatMessage[] = messages.slice(-12).map((m) => ({
      role: m.role,
      text: m.text,
    }));

    const userEntry: ChatEntry = {
      id: `u-${Date.now()}-${Math.random().toString(36).slice(2, 7)}`,
      role: 'user',
      text,
    };

    setMessages((prev) => [...prev, userEntry]);
    setInput('');
    setIsLoading(true);

    try {
      const result = await askNutritionAiAction(text, currentDate, historyPayload);

      if (!result.ok) {
        setErrorBanner({ status: result.status, message: result.error });
        return;
      }

      if (result.model) {
        setActiveModel(result.model);
      }

      const modelEntry: ChatEntry = {
        id: `m-${Date.now()}-${Math.random().toString(36).slice(2, 7)}`,
        role: 'model',
        text: result.reply,
      };
      setMessages((prev) => [...prev, modelEntry]);
    } catch {
      setErrorBanner({
        status: 503,
        message: 'Não foi possível comunicar com o assistente nutricional. Tente novamente.',
      });
    } finally {
      setIsLoading(false);
    }
  };

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    void sendMessage(input);
  };

  const handleKeyDown = (e: React.KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      void sendMessage(input);
    }
  };

  const formattedDateShort = new Date(currentDate + 'T00:00:00').toLocaleDateString('pt-BR', {
    day: '2-digit',
    month: '2-digit',
  });

  return (
    <>
      {/* Botão Flutuante Discreto no Canto Inferior Direito */}
      <button
        type="button"
        onClick={openDrawer}
        aria-label="Abrir assistente nutricional"
        aria-expanded={isOpen}
        aria-controls={dialogId}
        title="Copiloto Nutricional IA"
        className="fixed right-4 sm:right-6 bottom-[max(1.25rem,env(safe-area-inset-bottom))] z-40 min-w-[52px] min-h-[52px] px-3.5 sm:px-4 rounded-full bg-gradient-to-tr from-emerald-500 to-teal-400 hover:from-emerald-400 hover:to-teal-300 active:scale-95 text-slate-950 shadow-xl shadow-emerald-500/30 border border-emerald-300/40 inline-flex items-center justify-center gap-2 transition-all focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-emerald-300 focus-visible:ring-offset-2 focus-visible:ring-offset-slate-950"
      >
        <Sparkles className="w-5 h-5 shrink-0" aria-hidden="true" />
        <span className="hidden sm:inline text-xs font-black tracking-tight">Copiloto IA</span>
      </button>

      {/* Native Modal <dialog> responsivo (quase tela cheia no mobile, drawer direito no desktop) */}
      <dialog
        ref={dialogRef}
        id={dialogId}
        aria-labelledby={titleId}
        className="ai-drawer-dialog"
        {...({ closedby: 'any' } as Record<string, string>)}
      >
        <div
          ref={sheetRef}
          className="w-full sm:w-[440px] max-w-full h-[92svh] sm:h-[100svh] bg-slate-950 border-t sm:border-t-0 sm:border-l border-slate-800 rounded-t-3xl sm:rounded-none shadow-2xl flex flex-col overflow-hidden"
        >
          {/* Header do Drawer */}
          <div className="px-4 sm:px-5 py-3.5 border-b border-slate-800/80 bg-slate-900/80 flex items-center justify-between gap-2 shrink-0">
            <div className="flex items-center gap-2.5 min-w-0">
              <div className="w-9 h-9 rounded-xl bg-emerald-500/15 border border-emerald-500/30 flex items-center justify-center text-emerald-400 shrink-0">
                <Sparkles className="w-4 h-4" aria-hidden="true" />
              </div>
              <div className="min-w-0">
                <h1 id={titleId} className="text-sm font-extrabold text-white tracking-tight truncate">
                  Copiloto Nutricional
                </h1>
                <p className="text-[11px] text-slate-400 flex items-center gap-1.5 truncate">
                  <span className="w-1.5 h-1.5 rounded-full bg-emerald-400 shrink-0" />
                  Contexto real de {formattedDateShort} • {activeModel}
                </p>
              </div>
            </div>

            <div className="flex items-center gap-1 shrink-0">
              {messages.length > 0 && (
                <button
                  type="button"
                  onClick={() => {
                    setMessages([]);
                    setErrorBanner(null);
                  }}
                  title="Limpar conversa"
                  aria-label="Limpar conversa"
                  className="min-w-[40px] min-h-[40px] rounded-xl inline-flex items-center justify-center text-slate-400 hover:text-slate-200 hover:bg-slate-800/80 transition-colors"
                >
                  <Trash2 className="w-4 h-4" aria-hidden="true" />
                </button>
              )}
              <button
                type="button"
                onClick={closeDrawer}
                title="Fechar assistente"
                aria-label="Fechar assistente nutricional"
                className="min-w-[44px] min-h-[44px] rounded-xl inline-flex items-center justify-center text-slate-400 hover:text-white hover:bg-slate-800/80 transition-colors"
              >
                <X className="w-5 h-5" aria-hidden="true" />
              </button>
            </div>
          </div>

          {/* Área de Mensagens */}
          <div className="flex-1 overflow-y-auto px-4 sm:px-5 py-4 space-y-4">
            {messages.length === 0 ? (
              <div className="h-full flex flex-col justify-center space-y-5 py-4">
                <div className="text-center space-y-2">
                  <div className="w-12 h-12 rounded-2xl bg-emerald-500/10 border border-emerald-500/25 flex items-center justify-center text-emerald-400 mx-auto">
                    <Bot className="w-6 h-6" aria-hidden="true" />
                  </div>
                  <h2 className="text-sm font-bold text-white">
                    Seu copiloto nutricional de bolso
                  </h2>
                  <p className="text-xs text-slate-400 leading-relaxed max-w-xs mx-auto">
                    Eu leio suas metas, saldo restante de macros, água e refeições de <strong className="text-slate-200">{formattedDateShort}</strong> para sugerir ajustes práticos com gramagens exatas.
                  </p>
                </div>

                {/* 3 Chips de Sugestão Rápida */}
                <div className="space-y-2">
                  <p className="text-[11px] font-semibold uppercase tracking-wider text-slate-500 text-center">
                    Sugestões rápidas
                  </p>
                  <div className="flex flex-col gap-2">
                    {QUICK_SUGGESTIONS.map((suggestion) => (
                      <button
                        key={suggestion}
                        type="button"
                        disabled={isLoading}
                        onClick={() => void sendMessage(suggestion)}
                        className="w-full text-left px-3.5 py-3 rounded-2xl bg-slate-900/90 hover:bg-emerald-500/10 border border-slate-800 hover:border-emerald-500/35 text-xs font-medium text-slate-200 hover:text-emerald-200 transition-all flex items-center gap-2.5 disabled:opacity-50"
                      >
                        <Sparkles className="w-3.5 h-3.5 text-emerald-400 shrink-0" aria-hidden="true" />
                        <span>{suggestion}</span>
                      </button>
                    ))}
                  </div>
                </div>
              </div>
            ) : (
              <>
                {messages.map((msg) => {
                  const isUser = msg.role === 'user';
                  return (
                    <div
                      key={msg.id}
                      className={`flex items-start gap-2.5 ${isUser ? 'justify-end' : 'justify-start'}`}
                    >
                      {!isUser && (
                        <div className="w-7 h-7 rounded-lg bg-emerald-500/15 border border-emerald-500/30 flex items-center justify-center text-emerald-400 shrink-0 mt-0.5">
                          <Bot className="w-3.5 h-3.5" aria-hidden="true" />
                        </div>
                      )}

                      <div
                        className={`max-w-[85%] rounded-2xl px-3.5 py-2.5 ${
                          isUser
                            ? 'bg-emerald-500 text-slate-950 font-semibold text-xs sm:text-[13px] leading-relaxed rounded-tr-sm'
                            : 'bg-slate-900/90 border border-slate-800 rounded-tl-sm'
                        }`}
                      >
                        {isUser ? msg.text : <FormattedMarkdown text={msg.text} />}
                      </div>

                      {isUser && (
                        <div className="w-7 h-7 rounded-lg bg-slate-800 border border-slate-700 flex items-center justify-center text-slate-300 shrink-0 mt-0.5">
                          <UserIcon className="w-3.5 h-3.5" aria-hidden="true" />
                        </div>
                      )}
                    </div>
                  );
                })}

                {isLoading && (
                  <div className="flex items-start gap-2.5">
                    <div className="w-7 h-7 rounded-lg bg-emerald-500/15 border border-emerald-500/30 flex items-center justify-center text-emerald-400 shrink-0">
                      <Loader2 className="w-3.5 h-3.5 animate-spin" aria-hidden="true" />
                    </div>
                    <div className="bg-slate-900/90 border border-slate-800 rounded-2xl rounded-tl-sm px-3.5 py-2.5 text-xs text-slate-400 flex items-center gap-2">
                      <span>Calculando sugestões com seus macros do dia...</span>
                    </div>
                  </div>
                )}
              </>
            )}

            {errorBanner && (
              <div
                role="alert"
                className={`rounded-2xl p-3 border text-xs flex items-start gap-2.5 ${
                  errorBanner.status === 429
                    ? 'bg-amber-500/10 border-amber-500/30 text-amber-200'
                    : 'bg-rose-500/10 border-rose-500/30 text-rose-200'
                }`}
              >
                <AlertTriangle className="w-4 h-4 shrink-0 mt-0.5" aria-hidden="true" />
                <div className="space-y-0.5 leading-snug">
                  <p className="font-bold">
                    {errorBanner.status === 429
                      ? 'Limite de consultas atingido'
                      : errorBanner.status === 503
                      ? 'Assistente temporariamente indisponível'
                      : 'Não foi possível responder'}
                  </p>
                  <p className="opacity-90">{errorBanner.message}</p>
                </div>
              </div>
            )}

            <div ref={messagesEndRef} />
          </div>

          {/* Formulário de Envio */}
          <form
            onSubmit={handleSubmit}
            className="p-3 sm:p-4 border-t border-slate-800/80 bg-slate-900/80 pb-[max(0.75rem,env(safe-area-inset-bottom))] shrink-0"
          >
            <div className="flex items-end gap-2">
              <div className="flex-1 relative">
                <label htmlFor={`${dialogId}-input`} className="sr-only">
                  Pergunte ao copiloto nutricional
                </label>
                <textarea
                  ref={inputRef}
                  id={`${dialogId}-input`}
                  rows={1}
                  maxLength={1000}
                  value={input}
                  onChange={(e) => setInput(e.target.value)}
                  onKeyDown={handleKeyDown}
                  disabled={isLoading}
                  placeholder="Pergunte sobre seus macros, refeições ou substituições..."
                  className="w-full resize-none rounded-2xl bg-slate-950 border border-slate-700 focus:border-emerald-500 focus:outline-none px-3.5 py-2.5 text-xs sm:text-sm text-white placeholder-slate-500 max-h-28 disabled:opacity-60"
                />
                {input.length > 800 && (
                  <span className="absolute right-3 bottom-1 text-[10px] text-slate-500 tabular-nums">
                    {input.length}/1000
                  </span>
                )}
              </div>

              <button
                type="submit"
                disabled={isLoading || !input.trim()}
                aria-label="Enviar pergunta"
                className="min-w-[44px] min-h-[44px] rounded-2xl bg-emerald-500 hover:bg-emerald-400 active:scale-95 disabled:opacity-40 disabled:hover:bg-emerald-500 text-slate-950 inline-flex items-center justify-center transition-all shrink-0"
              >
                {isLoading ? (
                  <Loader2 className="w-4 h-4 animate-spin" aria-hidden="true" />
                ) : (
                  <Send className="w-4 h-4" aria-hidden="true" />
                )}
              </button>
            </div>
            <p className="text-[10px] text-slate-500 mt-1.5 text-center">
              Modo consultivo • Orientações baseadas no seu plano de {formattedDateShort}
            </p>
          </form>
        </div>
      </dialog>
    </>
  );
}
