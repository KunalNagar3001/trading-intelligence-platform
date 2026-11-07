-- Seed instruments so search, news and alerts work on a fresh database.
-- News is tagged when an instrument's symbol OR name appears in the headline text, so names are the
-- short forms headlines actually use ("Infosys", not "Infosys Ltd").
-- The upsert (needs MySQL 8.0.19+) inserts missing rows and corrects the names of existing ones,
-- so it is safe to run on every start.
INSERT INTO instruments (symbol, name, exchange, asset_type, active) VALUES
 ('INFY.NS',       'Infosys',                    'NSE', 'EQUITY',     true),
 ('TCS.NS',        'Tata Consultancy Services',  'NSE', 'EQUITY',     true),
 ('RELIANCE.NS',   'Reliance Industries',        'NSE', 'EQUITY',     true),
 ('HDFCBANK.NS',   'HDFC Bank',                  'NSE', 'EQUITY',     true),
 ('ICICIBANK.NS',  'ICICI Bank',                 'NSE', 'EQUITY',     true),
 ('SBIN.NS',       'State Bank of India',        'NSE', 'EQUITY',     true),
 ('GOLDBEES.NS',   'Gold BeES',                  'NSE', 'GOLD_ETF',   true),
 ('SILVERBEES.NS', 'Silver ETF',                 'NSE', 'SILVER_ETF', true)
AS new
ON DUPLICATE KEY UPDATE name = new.name;
