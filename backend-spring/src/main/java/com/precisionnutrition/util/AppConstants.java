package com.precisionnutrition.util;

import java.util.UUID;

public final class AppConstants {

    private AppConstants() {}

    /**
     * Identificador padrão de usuário para a operação do aplicativo em modo local / single-user.
     */
    public static final UUID DEFAULT_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
}
