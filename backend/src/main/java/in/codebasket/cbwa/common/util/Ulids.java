package in.codebasket.cbwa.common.util;

import com.github.f4b6a3.ulid.UlidCreator;

/** 26-character sortable public identifiers (never expose sequential ids publicly). */
public final class Ulids {

    private Ulids() {
    }

    public static String next() {
        return UlidCreator.getMonotonicUlid().toString();
    }
}
