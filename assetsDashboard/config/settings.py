from pathlib import Path

BASE_DIR = Path(__file__).resolve().parent.parent
SECRET_KEY = 'local-dev-only-change-me'
DEBUG = True
ALLOWED_HOSTS = ['127.0.0.1', 'localhost', 'testserver', '10.253.248.49']
INSTALLED_APPS = ['django.contrib.admin', 'django.contrib.auth', 'django.contrib.contenttypes',
                  'django.contrib.sessions', 'django.contrib.messages', 'django.contrib.staticfiles', 'portfolio']
MIDDLEWARE = ['django.middleware.security.SecurityMiddleware', 'django.contrib.sessions.middleware.SessionMiddleware', 'django.middleware.locale.LocaleMiddleware', 'django.middleware.common.CommonMiddleware', 'django.middleware.csrf.CsrfViewMiddleware',
              'django.contrib.auth.middleware.AuthenticationMiddleware', 'django.contrib.messages.middleware.MessageMiddleware', 'django.middleware.clickjacking.XFrameOptionsMiddleware']
ROOT_URLCONF = 'config.urls'
TEMPLATES = [{'BACKEND': 'django.template.backends.django.DjangoTemplates', 'DIRS': [BASE_DIR / 'templates'], 'APP_DIRS': True, 'OPTIONS': {'context_processors': [
    'django.template.context_processors.request', 'django.template.context_processors.i18n', 'django.contrib.auth.context_processors.auth', 'django.contrib.messages.context_processors.messages']}}]
WSGI_APPLICATION = 'config.wsgi.application'
DATABASES = {'default': {'ENGINE': 'django.db.backends.sqlite3',
                         'NAME': BASE_DIR / 'db.sqlite3'}}
LANGUAGE_CODE = 'en-us'
LANGUAGES = [('en', 'English'), ('fa', 'فارسی')]
LOCALE_PATHS = [BASE_DIR / 'locale']
TIME_ZONE = 'Asia/Tehran'
USE_I18N = True
USE_THOUSAND_SEPARATOR = True
USE_TZ = True
STATIC_URL = 'static/'
STATICFILES_DIRS = [BASE_DIR / 'static']
DEFAULT_AUTO_FIELD = 'django.db.models.BigAutoField'
PRICE_REFRESH_MIN_INTERVAL = 60
NOBITEX_BASE_URL = 'https://apiv2.nobitex.ir'
NOBITEX_CONNECT_TIMEOUT = 3
NOBITEX_READ_TIMEOUT = 5
ABANTETHER_BASE_URL = 'https://api.abantether.com/api/v1'
ABANTETHER_COIN_CATALOG_URL = 'https://api.abantether.com/api/v2/manager/coins'
ABANTETHER_CONNECT_TIMEOUT = 3
ABANTETHER_READ_TIMEOUT = 8
TSETMC_BASE_URL = 'https://cdn.tsetmc.com/api'
TSETMC_CONNECT_TIMEOUT = 3
TSETMC_READ_TIMEOUT = 8
RAHAVARD_BASE_URL = 'https://rahavard365.com/api/v2'
RAHAVARD_CONNECT_TIMEOUT = 3
RAHAVARD_READ_TIMEOUT = 8
LOGGING = {
    'version': 1,
    'disable_existing_loggers': False,
    'formatters': {'verbose': {'format': '{asctime} {levelname} {name}: {message}', 'style': '{'}},
    'handlers': {
        'console': {'class': 'logging.StreamHandler', 'formatter': 'verbose'},
        'portfolio_file': {'class': 'logging.FileHandler', 'filename': BASE_DIR / 'logs' / 'portfolio.log', 'formatter': 'verbose'},
    },
    'loggers': {'portfolio': {'handlers': ['console', 'portfolio_file'], 'level': 'INFO', 'propagate': False}},
}
