-- Seed instruments so search, news and alerts work on a fresh database.
-- INSERT IGNORE + the unique constraint on symbol makes this safe to run on every start.
INSERT IGNORE INTO instruments (symbol, name, exchange, asset_type, active) VALUES
 ('INFY.NS',       'Infosys Ltd',                    'NSE', 'EQUITY',     true),
 ('TCS.NS',        'Tata Consultancy Services Ltd',  'NSE', 'EQUITY',     true),
 ('RELIANCE.NS',   'Reliance Industries Ltd',        'NSE', 'EQUITY',     true),
 ('HDFCBANK.NS',   'HDFC Bank Ltd',                  'NSE', 'EQUITY',     true),
 ('ICICIBANK.NS',  'ICICI Bank Ltd',                 'NSE', 'EQUITY',     true),
 ('SBIN.NS',       'State Bank of India',            'NSE', 'EQUITY',     true),
 ('GOLDBEES.NS',   'Nippon India ETF Gold BeES',     'NSE', 'GOLD_ETF',   true),
 ('SILVERBEES.NS', 'Nippon India Silver ETF',        'NSE', 'SILVER_ETF', true);
