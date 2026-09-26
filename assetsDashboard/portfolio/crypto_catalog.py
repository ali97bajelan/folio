"""Top-100 cryptocurrency discovery catalogue (market-cap snapshot: 2026-09-19).

The catalogue is deliberately local: it provides fast name suggestions without
depending on an exchange endpoint that only accepts ticker symbols.  Nobitex is
still the source of truth for whether a suggested asset has an active market.
"""
from unicodedata import normalize as unicode_normalize

TOP_CRYPTO = (
    ('BTC', 'Bitcoin'), ('ETH', 'Ethereum'), ('USDT', 'Tether'), ('BNB', 'BNB'), ('XRP', 'XRP'),
    ('USDC', 'USDC'), ('SOL', 'Solana'), ('TRX', 'TRON'), ('ZEC', 'Zcash'), ('FIGR_HELOC', 'Figure Heloc'),
    ('HYPE', 'Hyperliquid'), ('DOGE', 'Dogecoin'), ('XMR', 'Monero'), ('WBT', 'WhiteBIT Coin'), ('RAIN', 'Rain'),
    ('USDS', 'USDS'), ('LINK', 'Chainlink'), ('ADA', 'Cardano'), ('LEO', 'LEO Token'), ('XLM', 'Stellar'),
    ('UNI', 'Uniswap'), ('BCH', 'Bitcoin Cash'), ('NEAR', 'NEAR Protocol'), ('USDE', 'Ethena USDe'), ('DAI', 'Dai'),
    ('LTC', 'Litecoin'), ('USD1', 'USD1'), ('CC', 'Canton'), ('AVAX', 'Avalanche'), ('GRAM', 'Gram'),
    ('HBAR', 'Hedera'), ('SUI', 'Sui'), ('USDG', 'Global Dollar'), ('SHIB', 'Shiba Inu'), ('M', 'MemeCore'),
    ('CRO', 'Cronos'), ('TAO', 'Bittensor'), ('PYUSD', 'PayPal USD'), ('XAUT', 'Tether Gold'), ('USYC', 'Circle USYC'),
    ('OKB', 'OKB'), ('RLUSD', 'Ripple USD'), ('BUIDL', 'BlackRock USD Institutional Digital Liquidity Fund'), ('USDY', 'Ondo US Dollar Yield'), ('AAVE', 'Aave'),
    ('ASTER', 'Aster'), ('MNT', 'Mantle'), ('ONDO', 'Ondo'), ('ENA', 'Ethena'), ('PUMP', 'Pump.fun'),
    ('MORPHO', 'Morpho'), ('DOT', 'Polkadot'), ('PAXG', 'PAX Gold'), ('WLFI', 'World Liberty Financial'), ('SKY', 'Sky'),
    ('BTW', 'Bitway'), ('ICP', 'Internet Computer'), ('PEPE', 'Pepe'), ('HTX', 'HTX DAO'), ('USDD', 'USDD'),
    ('WLD', 'Worldcoin'), ('ARB', 'Arbitrum'), ('EURSAFO', 'Spiko Amundi Overnight Swap Fund (EUR)'), ('BGB', 'Bitget Token'), ('U', 'United Stables'),
    ('USDGO', 'USDGO'), ('VVV', 'Venice Token'), ('USDF', 'Falcon USD'), ('AKE', 'Akedo'), ('BFUSD', 'BFUSD'),
    ('ETC', 'Ethereum Classic'), ('LIT', 'Lighter'), ('POL', 'POL (ex-MATIC)'), ('GT', 'Gate'), ('KAS', 'Kaspa'),
    ('KCS', 'KuCoin'), ('BCAP', 'Blockchain Capital'), ('JST', 'JUST'), ('PI', 'Pi Network'), ('QNT', 'Quant'),
    ('JUP', 'Jupiter'), ('ATOM', 'Cosmos Hub'), ('ALGO', 'Algorand'), ('CAKE', 'PancakeSwap'), ('NEXO', 'NEXO'),
    ('FIL', 'Filecoin'), ('RENDER', 'Render'), ('EUTBL', 'Spiko EU T-Bills Money Market Fund'), ('JAAA', 'Janus Henderson Anemoy AAA CLO Fund'), ('DASH', 'Dash'),
    ('INJ', 'Injective'), ('USTB', 'Invesco Short Duration US Government Securities Fund'), ('VET', 'VeChain'), ('ETHFI', 'Ether.fi'), ('GHO', 'GHO'),
    ('STABLE', 'Stable'), ('AERO', 'Aerodrome Finance'), ('APT', 'Aptos'), ('BDX', 'Beldex'), ('FLR', 'Flare'),
)

# Frequent Persian spellings.  Symbols and English names above remain searchable
# as well; aliases make the common Persian terms work without a translation API.
PERSIAN_ALIASES = {
    'بیت کوین': 'BTC', 'بیتکوین': 'BTC', 'اتریوم': 'ETH', 'تتر': 'USDT',
    'بایننس کوین': 'BNB', 'ریپل': 'XRP', 'یو اس دی کوین': 'USDC', 'سولانا': 'SOL',
    'ترون': 'TRX', 'زی کش': 'ZEC', 'دوج کوین': 'DOGE', 'مونرو': 'XMR',
    'چین لینک': 'LINK', 'کاردانو': 'ADA', 'استلار': 'XLM', 'یونی سواپ': 'UNI',
    'بیت کوین کش': 'BCH', 'لایت کوین': 'LTC', 'آوالانچ': 'AVAX', 'هدرا': 'HBAR',
    'شیبا': 'SHIB', 'پولکادات': 'DOT', 'پکس گلد': 'PAXG', 'اینترنت کامپیوتر': 'ICP',
    'ورلد کوین': 'WLD', 'آربیتروم': 'ARB', 'اتریوم کلاسیک': 'ETC', 'کسپا': 'KAS',
    'پای نتورک': 'PI', 'کازماس': 'ATOM', 'الگوراند': 'ALGO', 'فایل کوین': 'FIL',
    'دش': 'DASH', 'اینجکتیو': 'INJ', 'وی چین': 'VET', 'آپتوس': 'APT', 'فِلر': 'FLR',
}

GLOBAL_STOCK_ALIASES = {
    'apple': 'AAPL', 'اپل': 'AAPL', 'microsoft': 'MSFT', 'مایکروسافت': 'MSFT',
    'nvidia': 'NVDA', 'انویدیا': 'NVDA', 'amazon': 'AMZN', 'آمازون': 'AMZN',
    'alphabet': 'GOOGL', 'google': 'GOOGL', 'گوگل': 'GOOGL', 'meta': 'META',
    'tesla': 'TSLA', 'تسلا': 'TSLA', 'netflix': 'NFLX', 'نتفلیکس': 'NFLX',
    'intel': 'INTC', 'اینتل': 'INTC', 'amd': 'AMD', 'coca cola': 'KO',
    'کوکاکولا': 'KO', 'disney': 'DIS', 'دیزنی': 'DIS', 'nike': 'NKE', 'نایکی': 'NKE',
    'visa': 'V', 'mastercard': 'MA', 'mcdonalds': 'MCD', 'مک دونالد': 'MCD',
    'walmart': 'WMT', 'والمارت': 'WMT', 'paypal': 'PYPL', 'پی پل': 'PYPL',
}

def normalized(value):
    """Normalize Persian/Arabic spelling and punctuation for local searching."""
    return ''.join(unicode_normalize('NFKC', value or '').lower().replace('ي', 'ی').replace('ك', 'ک').split())

def find_symbol(query):
    """Resolve an exact or partial local name/symbol to one crypto ticker."""
    needle = normalized(query)
    if not needle:
        return None
    for name, symbol in PERSIAN_ALIASES.items():
        if needle in normalized(name):
            return symbol
    for symbol, name in TOP_CRYPTO:
        if needle in normalized(symbol) or needle in normalized(name):
            return symbol
    return None

def find_global_stock_symbol(query):
    needle = normalized(query)
    if not needle:
        return None
    for name, symbol in GLOBAL_STOCK_ALIASES.items():
        if needle in normalized(name):
            return symbol
    return None
