CREATE TABLE notifications (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    user_id    BIGINT       NOT NULL,
    order_id   BIGINT,
    type       VARCHAR(32)  NOT NULL,
    subject    VARCHAR(200) NOT NULL,
    body       VARCHAR(2000) NOT NULL,
    created_at DATETIME(6)  NOT NULL,
    CONSTRAINT pk_notifications PRIMARY KEY (id)
);

CREATE INDEX idx_notifications_user_created ON notifications (user_id, created_at);
