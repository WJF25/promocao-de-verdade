CREATE TABLE IF NOT EXISTS price_logs (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  product_name VARCHAR(255) NOT NULL,
  site_name VARCHAR(255) NOT NULL,
  price_original DECIMAL(12,2),
  price_cash DECIMAL(12,2) NOT NULL,
  price_installment DECIMAL(12,2),
  max_installments INTEGER,
  url TEXT NOT NULL,
  captured_at DATE DEFAULT CURRENT_DATE,
  CONSTRAINT price_logs_user_id_product_name_site_name_captured_at_key 
    UNIQUE (user_id, product_name, site_name, captured_at)
);