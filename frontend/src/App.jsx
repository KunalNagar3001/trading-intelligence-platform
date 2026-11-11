import { useCallback, useEffect, useRef, useState } from 'react';

// ---------- helpers ----------
const money = (n) => (n == null ? '–' : '₹' + Number(n).toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 }));
const pct = (n) => (n == null ? '–' : (n >= 0 ? '+' : '') + Number(n).toFixed(2) + '%');
const tone = (n) => (n > 0 ? 'gain' : n < 0 ? 'loss' : '');

async function api(path, token, options = {}) {
  const res = await fetch(path, {
    ...options,
    headers: { 'Content-Type': 'application/json', ...(token && { Authorization: `Bearer ${token}` }) },
  });
  const text = await res.text();
  if (!res.ok) {
    let message = res.statusText;
    try { message = JSON.parse(text).message || message; } catch { /* body was not JSON */ }
    const err = new Error(message || `Request failed (${res.status})`);
    err.status = res.status;
    throw err;
  }
  try { return JSON.parse(text); } catch { return text; } // login returns a bare token string
}

// Live prices from the backend WebSocket (/ws/prices). The first message authenticates with the JWT; the
// server then sends only prices for symbols this user holds or has alerts on. Reconnects automatically.
function useLivePrices(token, onAuthFail) {
  const [prices, setPrices] = useState({}); // symbol -> { price, dir }
  const [connected, setConnected] = useState(false);
  useEffect(() => {
    setPrices({}); // a different user must never see the previous user's prices
    let socket, timer, stopped = false;
    const connect = () => {
      const scheme = window.location.protocol === 'https:' ? 'wss' : 'ws';
      socket = new WebSocket(`${scheme}://${window.location.host}/ws/prices`);
      socket.onopen = () => socket.send(JSON.stringify({ type: 'auth', token }));
      socket.onmessage = (e) => {
        const msg = JSON.parse(e.data);
        if (msg.type === 'ready') { setConnected(true); return; }
        if (msg.symbol == null) return;
        const { symbol, price } = msg;
        setPrices((prev) => {
          const before = prev[symbol]?.price;
          const dir = before == null || price === before ? prev[symbol]?.dir : price > before ? 'up' : 'down';
          return { ...prev, [symbol]: { price, dir, tick: (prev[symbol]?.tick || 0) + 1 } };
        });
      };
      socket.onclose = (e) => {
        setConnected(false);
        if (e.code === 1008) { if (!stopped) onAuthFail(); return; } // token rejected: reconnecting cannot help
        if (!stopped) timer = setTimeout(connect, 2000);
      };
    };
    connect();
    return () => { stopped = true; clearTimeout(timer); socket?.close(); };
  }, [token, onAuthFail]);
  return { prices, connected };
}

// Symbol field with suggestions from GET /api/stocks/search (Yahoo). Shows Indian listings (.NS first, then .BO).
function SymbolInput({ value, onChange, search, placeholder }) {
  const [items, setItems] = useState([]);
  const [open, setOpen] = useState(false);
  const [active, setActive] = useState(-1);
  const latest = useRef(0);      // ignores responses that arrive after a newer search
  const justPicked = useRef(false); // choosing a suggestion must not trigger another search

  useEffect(() => {
    if (justPicked.current) { justPicked.current = false; return; }
    const q = value.trim();
    if (q.length < 2) { setItems([]); return; }
    const id = ++latest.current;
    const timer = setTimeout(async () => {
      try {
        const results = await search(q);
        if (id !== latest.current) return;
        const indian = results.filter((r) => /\.(NS|BO)$/.test(r.symbol))
          .sort((a, b) => Number(b.symbol.endsWith('.NS')) - Number(a.symbol.endsWith('.NS')));
        setItems(indian.slice(0, 8)); setActive(-1); setOpen(true);
      } catch { if (id === latest.current) setItems([]); }
    }, 300);
    return () => clearTimeout(timer);
  }, [value]); // eslint-disable-line react-hooks/exhaustive-deps

  const pick = (item) => { justPicked.current = true; onChange(item.symbol); setOpen(false); setItems([]); };
  const onKeyDown = (e) => {
    if (!open || items.length === 0) return;
    if (e.key === 'ArrowDown') { e.preventDefault(); setActive((a) => Math.min(a + 1, items.length - 1)); }
    else if (e.key === 'ArrowUp') { e.preventDefault(); setActive((a) => Math.max(a - 1, 0)); }
    else if (e.key === 'Enter' && active >= 0) { e.preventDefault(); pick(items[active]); }
    else if (e.key === 'Escape') setOpen(false);
  };

  return (
    <div className="suggest-wrap">
      <input placeholder={placeholder} value={value} required autoComplete="off"
        onChange={(e) => { onChange(e.target.value); setOpen(true); }}
        onKeyDown={onKeyDown} onBlur={() => setOpen(false)} onFocus={() => items.length && setOpen(true)} />
      {open && items.length > 0 && (
        <ul className="suggest">
          {items.map((item, i) => (
            <li key={item.symbol} className={i === active ? 'active' : ''}
              onMouseDown={(e) => { e.preventDefault(); pick(item); }}>
              <span><strong>{item.symbol}</strong> <small>{item.name}</small></span>
              <small>{item.exchange}</small>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

// ---------- login ----------
function Login({ onToken }) {
  const [mode, setMode] = useState('login');
  const [form, setForm] = useState({ name: '', email: '', password: '' });
  const [error, setError] = useState('');
  const set = (k) => (e) => setForm({ ...form, [k]: e.target.value });

  const submit = async (e) => {
    e.preventDefault();
    setError('');
    try {
      if (mode === 'register') await api('/api/auth/register', null, { method: 'POST', body: JSON.stringify(form) });
      onToken(await api('/api/auth/login', null, { method: 'POST', body: JSON.stringify({ email: form.email, password: form.password }) }));
    } catch (err) { setError(err.message); }
  };

  return (
    <form className="panel login" onSubmit={submit}>
      <h1>Trading Intelligence Platform</h1>
      <p className="empty">{mode === 'login' ? 'Sign in to see your portfolio.' : 'Create an account.'}</p>
      {mode === 'register' && <input placeholder="Name" value={form.name} onChange={set('name')} required />}
      <input type="email" placeholder="Email" value={form.email} onChange={set('email')} required />
      <input type="password" placeholder="Password" value={form.password} onChange={set('password')} required />
      <div className="row">
        <button type="submit">{mode === 'login' ? 'Sign in' : 'Register'}</button>
        <button type="button" className="ghost" onClick={() => setMode(mode === 'login' ? 'register' : 'login')}>
          {mode === 'login' ? 'New here?' : 'Have an account?'}
        </button>
      </div>
      {error && <p className="err">{error}</p>}
    </form>
  );
}

// ---------- dashboard ----------
function Dashboard({ token, onLogout }) {
  const { prices, connected } = useLivePrices(token, onLogout);
  const [summary, setSummary] = useState(null);
  const [alerts, setAlerts] = useState([]);
  const [error, setError] = useState('');
  const [trade, setTrade] = useState({ symbol: '', quantity: '', assetType: 'EQUITY' });
  const [alert, setAlert] = useState({ symbol: '', alertType: 'TARGET_PRICE', condition: 'ABOVE', value: '' });

  const call = useCallback(async (path, options) => {
    try { return await api(path, token, options); }
    catch (err) { if (err.status === 401 || err.status === 403) onLogout(); throw err; }
  }, [token, onLogout]);

  const searchStocks = useCallback((q) => call('/api/stocks/search?q=' + encodeURIComponent(q)), [call]);

  const reload = useCallback(async () => {
    try {
      const [s, a] = await Promise.all([call('/api/portfolio/summary'), call('/api/alerts')]);
      setSummary(s); setAlerts(a);
    } catch (err) { setError(err.message); }
  }, [call]);
  useEffect(() => { reload(); }, [reload]);

  const act = (fn) => async (e) => {
    e.preventDefault(); setError('');
    try { await fn(); await reload(); } catch (err) { setError(err.message); }
  };

  const placeTrade = (side) => act(() => call(`/api/portfolio/${side}`, {
    method: 'POST',
    body: JSON.stringify({
      symbol: trade.symbol.trim().toUpperCase(), quantity: Number(trade.quantity),
      assetType: trade.assetType, date: new Date().toISOString().slice(0, 10),
    }),
  }));

  const createAlert = act(() => {
    const isPercent = alert.alertType === 'PERCENTAGE_CHANGE';
    return call('/api/alerts', {
      method: 'POST',
      body: JSON.stringify({
        symbol: alert.symbol.trim().toUpperCase(), alertType: alert.alertType, condition: alert.condition,
        ...(isPercent ? { percentageThreshold: Number(alert.value) } : { targetPrice: Number(alert.value) }),
      }),
    });
  });

  // Re-value every holding with the latest WebSocket price, falling back to the API's price.
  const rows = (summary?.holdings || []).map((h) => {
    const live = prices[h.symbol];
    const price = live ? live.price : Number(h.currentPrice);
    const value = price * h.quantity;
    const invested = Number(h.investedValue);
    return { ...h, live, price, value, pl: value - invested, plPct: invested ? ((value - invested) / invested) * 100 : 0 };
  });
  const invested = rows.reduce((s, r) => s + Number(r.investedValue), 0);
  const value = rows.reduce((s, r) => s + r.value, 0);

  return (
    <div className="wrap">
      <header className="top">
        <h1>Trading Intelligence Platform</h1>
        <div className="row" style={{ margin: 0, alignItems: 'center' }}>
          <span className="status"><span className={`dot ${connected ? 'on' : ''}`} />{connected ? 'Live' : 'Reconnecting…'}</span>
          <button className="ghost" onClick={onLogout}>Sign out</button>
        </div>
      </header>
      {error && <p className="err">{error}</p>}

      <div className="grid">
        <section className="panel">
          <h2>Portfolio</h2>
          <div className="totals num">
            <div><span>Invested</span><strong>{money(invested)}</strong></div>
            <div><span>Current value</span><strong>{money(value)}</strong></div>
            <div><span>Profit / loss</span>
              <strong className={tone(value - invested)}>{money(value - invested)} ({pct(invested ? ((value - invested) / invested) * 100 : 0)})</strong></div>
          </div>
          {rows.length === 0 ? <p className="empty">No holdings yet. Buy something below.</p> : (
            <div className="scroll"><table>
              <thead><tr><th>Symbol</th><th className="r">Qty</th><th className="r">Avg buy</th><th className="r">Price</th><th className="r">P&amp;L</th></tr></thead>
              <tbody>{rows.map((r) => (
                <tr key={r.symbol}>
                  <td>{r.symbol}{r.priceStale && !r.live && <span className="stale">stale</span>}</td>
                  <td className="r num">{r.quantity}</td>
                  <td className="r num">{money(r.buyPrice)}</td>
                  <td key={r.live?.tick} className={`r num ${r.live?.dir ? 'flash-' + r.live.dir : ''}`}>{money(r.price)}</td>
                  <td className={`r num ${tone(r.pl)}`}>{money(r.pl)} <small>{pct(r.plPct)}</small></td>
                </tr>))}
              </tbody>
            </table></div>
          )}
          <form className="row">
            <SymbolInput placeholder="Search stock, e.g. Tata Steel" value={trade.symbol} search={searchStocks} onChange={(v) => setTrade({ ...trade, symbol: v })} />
            <input type="number" min="1" placeholder="Qty" value={trade.quantity} onChange={(e) => setTrade({ ...trade, quantity: e.target.value })} required style={{ maxWidth: 80 }} />
            <select value={trade.assetType} onChange={(e) => setTrade({ ...trade, assetType: e.target.value })}>
              <option>EQUITY</option><option>GOLD_ETF</option><option>SILVER_ETF</option>
            </select>
            <button onClick={placeTrade('buy')}>Buy</button>
            <button className="ghost" onClick={placeTrade('sell')}>Sell</button>
          </form>
        </section>

        <section className="panel">
          <h2>Live prices</h2>
          {Object.keys(prices).length === 0
            ? <p className="empty">Waiting for prices. Symbols you hold or have alerts on stream here every few seconds.</p>
            : <div className="scroll"><table><tbody>{Object.entries(prices).map(([symbol, p]) => (
                <tr key={symbol}><td>{symbol}</td>
                  <td key={p.tick} className={`r num ${p.dir ? 'flash-' + p.dir : ''}`}>{money(p.price)}</td></tr>))}
              </tbody></table></div>}
        </section>

        <section className="panel" style={{ gridColumn: '1 / -1' }}>
          <h2>Alerts</h2>
          {alerts.length === 0 ? <p className="empty">No active alerts.</p> : (
            <div className="scroll"><table>
              <thead><tr><th>Symbol</th><th>Type</th><th>Condition</th><th className="r">Trigger</th><th /></tr></thead>
              <tbody>{alerts.map((a) => (
                <tr key={a.id}>
                  <td>{a.symbol}</td><td>{a.alertType.replace('_', ' ').toLowerCase()}</td><td>{a.condition.toLowerCase()}</td>
                  <td className="r num">{a.alertType === 'PERCENTAGE_CHANGE' ? `${a.percentageThreshold}% from ${money(a.basePrice)}` : money(a.targetPrice)}</td>
                  <td className="r"><button className="danger" onClick={act(() => call(`/api/alerts/${a.id}`, { method: 'DELETE' }))}>Delete</button></td>
                </tr>))}
              </tbody>
            </table></div>
          )}
          <form className="row" onSubmit={createAlert}>
            <SymbolInput placeholder="Search stock" value={alert.symbol} search={searchStocks} onChange={(v) => setAlert({ ...alert, symbol: v })} />
            <select value={alert.alertType} onChange={(e) => setAlert({ ...alert, alertType: e.target.value })}>
              <option value="TARGET_PRICE">Target price</option><option value="STOP_LOSS">Stop loss</option><option value="PERCENTAGE_CHANGE">Percentage change</option>
            </select>
            <select value={alert.condition} onChange={(e) => setAlert({ ...alert, condition: e.target.value })} disabled={alert.alertType === 'STOP_LOSS'}>
              <option value="ABOVE">Above</option><option value="BELOW">Below</option>
            </select>
            <input type="number" step="any" min="0" placeholder={alert.alertType === 'PERCENTAGE_CHANGE' ? '% move' : '₹ price'}
              value={alert.value} onChange={(e) => setAlert({ ...alert, value: e.target.value })} required />
            <button type="submit">Add alert</button>
          </form>
        </section>
      </div>
    </div>
  );
}

// ---------- root ----------
export default function App() {
  const [token, setToken] = useState(() => sessionStorage.getItem('token'));
  const saveToken = (t) => { sessionStorage.setItem('token', t); setToken(t); };
  const logout = useCallback(() => { sessionStorage.removeItem('token'); setToken(null); }, []);
  return token ? <Dashboard token={token} onLogout={logout} /> : <Login onToken={saveToken} />;
}
