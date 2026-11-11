CREATE TABLE menu_categories (
    code VARCHAR(32) CHARACTER SET ascii COLLATE ascii_general_ci NOT NULL,
    name VARCHAR(120) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_menu_categories PRIMARY KEY (code),
    CONSTRAINT ck_menu_categories_name CHECK (CHAR_LENGTH(TRIM(name)) > 0),
    CONSTRAINT ck_menu_categories_version CHECK (version >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE menu_items (
    code VARCHAR(40) CHARACTER SET ascii COLLATE ascii_general_ci NOT NULL,
    name VARCHAR(160) NOT NULL,
    price DECIMAL(12,0) NOT NULL,
    category_code VARCHAR(32) CHARACTER SET ascii COLLATE ascii_general_ci NOT NULL,
    image_url VARCHAR(1024) NOT NULL,
    description VARCHAR(1000) NOT NULL DEFAULT '',
    available BOOLEAN NOT NULL DEFAULT TRUE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_menu_items PRIMARY KEY (code),
    CONSTRAINT fk_menu_items_category FOREIGN KEY (category_code) REFERENCES menu_categories(code),
    CONSTRAINT ck_menu_items_name CHECK (CHAR_LENGTH(TRIM(name)) >= 6),
    CONSTRAINT ck_menu_items_price CHECK (price > 5000),
    CONSTRAINT ck_menu_items_version CHECK (version >= 0),
    INDEX ix_menu_items_category_active (category_code, active),
    INDEX ix_menu_items_active_price (active, price)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
