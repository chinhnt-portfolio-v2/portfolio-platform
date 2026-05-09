-- V18: Wishlist items for purchase planning

CREATE TABLE wishlist_items (
    id              BIGSERIAL PRIMARY KEY,
    user_id         UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name            VARCHAR(255) NOT NULL,
    estimated_price DECIMAL(15,2),
    currency        VARCHAR(3)   DEFAULT 'VND',
    priority        VARCHAR(10)  NOT NULL DEFAULT 'MEDIUM' CHECK (priority IN ('HIGH', 'MEDIUM', 'LOW')),
    status          VARCHAR(15)  NOT NULL DEFAULT 'SAVING' CHECK (status IN ('SAVING', 'PURCHASED', 'CANCELLED')),
    target_date     DATE,
    notes           TEXT,
    url             TEXT,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_wishlist_user_status   ON wishlist_items(user_id, status);
CREATE INDEX idx_wishlist_user_priority ON wishlist_items(user_id, priority);
