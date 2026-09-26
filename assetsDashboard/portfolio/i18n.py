"""Small UI translation catalogue used by the portfolio interface.

The application keeps its URLs stable while Django's LocaleMiddleware stores the
chosen language in the session/cookie.  This catalogue covers the concise UI
copy and lets existing user-entered asset, tag, and location names remain intact.
"""
from django.utils.translation import get_language


FA = {
    'Overview': 'نمای کلی', 'Assets': 'دارایی‌ها', 'Transactions': 'تراکنش‌ها',
    'Tags': 'برچسب‌ها', 'Locations': 'مکان‌ها', 'PRIVATE WEALTH': 'دارایی شخصی',
    'PRIVATE & LOCAL': 'خصوصی و محلی', 'SQLite portfolio tracker': 'ردیاب پرتفوی SQLite',
    'PERSONAL WEALTH': 'دارایی شخصی', 'Dashboard': 'داشبورد',
    '+ Transaction': '+ تراکنش', '+ Add asset': '+ افزودن دارایی',
    '↻ Refresh prices': '↻ به‌روزرسانی قیمت‌ها', 'English': 'English', 'فارسی': 'فارسی',
    'TOTAL PORTFOLIO VALUE': 'ارزش کل پرتفوی', 'awaiting a price': 'دارایی در انتظار قیمت',
    'Toman': 'تومان',
    'asset': 'دارایی', 'assets': 'دارایی', 'ACTIVE ASSETS': 'دارایی‌های فعال',
    'Across your portfolio': 'در سراسر پرتفوی شما', 'LAST PRICE UPDATE': 'آخرین به‌روزرسانی قیمت',
    'ago': 'پیش', 'Stored prices work offline': 'قیمت‌های ذخیره‌شده آفلاین کار می‌کنند',
    'PORTFOLIO CHANGE': 'تغییر پرتفوی', 'Compared with each prior period': 'در مقایسه با هر بازهٔ گذشته',
    'Portfolio trend': 'روند پرتفوی', 'Reconstructed value': 'ارزش بازسازی‌شده',
    'By asset class': 'بر اساس نوع دارایی', 'Current allocation': 'تخصیص فعلی',
    'Tagged allocation': 'تخصیص برچسب‌خورده', 'Assets sharing a tag': 'دارایی‌های دارای برچسب مشترک',
    'Select tag': 'برچسب را انتخاب کنید', 'No matching valued assets.': 'دارایی ارزش‌گذاری‌شدهٔ منطبقی نیست.',
    'Choose a tag to inspect its allocation.': 'برای بررسی تخصیص، یک برچسب انتخاب کنید.',
    'Quick start': 'شروع سریع', 'Your data stays on this machine.': 'داده‌های شما روی همین دستگاه می‌ماند.',
    'Add an asset and optional opening balance.': 'یک دارایی و موجودی آغازین اختیاری اضافه کنید.',
    'Record buys and sells by location.': 'خرید و فروش را بر اساس مکان ثبت کنید.',
    'Enter manual prices or attach a provider.': 'قیمت دستی وارد کنید یا یک ارائه‌دهنده متصل کنید.',
    'Latest holdings and values': 'آخرین موجودی‌ها و ارزش‌ها', 'View all →': 'مشاهدهٔ همه ←',
    'Asset': 'دارایی', 'Quantity': 'تعداد', 'Current price': 'قیمت فعلی', 'Value': 'ارزش',
    'No assets yet': 'هنوز دارایی‌ای ندارید', 'Add your first asset to start tracking your portfolio.': 'برای شروع ردیابی پرتفوی، اولین دارایی را اضافه کنید.',
    'Add asset': 'افزودن دارایی', 'Current holdings and prices': 'موجودی‌ها و قیمت‌های فعلی',
    'Type': 'نوع', 'Price': 'قیمت', 'Value (USDT)': 'ارزش (تتر)', 'Value (Toman)': 'ارزش (تومان)',
    'Details →': 'جزئیات ←', 'No active assets.': 'دارایی فعالی وجود ندارد.', 'Unavailable': 'ناموجود',
    'Edit asset': 'ویرایش دارایی', 'Update price': 'به‌روزرسانی قیمت', 'Delete asset': 'حذف دارایی',
    'CURRENT VALUE': 'ارزش فعلی', 'AVERAGE COST': 'میانگین هزینه', 'QUANTITY': 'تعداد',
    'Price at': 'قیمت در', 'Holdings by location': 'موجودی بر اساس مکان', 'No holdings recorded.': 'موجودی‌ای ثبت نشده است.',
    'No location': 'بدون مکان', 'Asset details': 'جزئیات دارایی', 'Symbol': 'نماد', 'Pricing': 'قیمت‌گذاری',
    'Provider': 'ارائه‌دهنده', 'Position source of truth': 'منبع اصلی موقعیت', 'Date': 'تاریخ',
    'Unit price': 'قیمت واحد', 'Location': 'مکان', 'Edit': 'ویرایش', 'Delete': 'حذف',
    'No transactions yet.': 'هنوز تراکنشی ثبت نشده است.', 'transaction': 'تراکنش',
    'Delete this': 'حذف این', 'This cannot be undone.': 'این عمل قابل بازگشت نیست.', 'Cancel': 'لغو',
    'Matching instruments': 'ابزارهای منطبق', 'Enter an asset name, then choose a suggested market.': 'نام دارایی را وارد کنید و سپس یک بازار پیشنهادی را انتخاب کنید.',
    'Opening holdings by location': 'موجودی آغازین بر اساس مکان', 'Add one row for each exchange or wallet where you already hold this asset.': 'برای هر صرافی یا کیف پولی که این دارایی را در آن دارید، یک ردیف اضافه کنید.',
    '+ Add another holding': '+ افزودن موجودی دیگر', 'Saved': 'ذخیره‌شده', 'Add': 'افزودن', 'Nothing here yet.': 'هنوز چیزی اینجا نیست.',
    'All time': 'همهٔ زمان‌ها', '1 year': '۱ سال', '3 months': '۳ ماه', '1 month': '۱ ماه', '1 week': '۱ هفته', '1 day': '۱ روز', '7 days': '۷ روز', '30 days': '۳۰ روز',
    'Fixed income': 'درآمد ثابت', 'Crypto': 'رمزارز', 'Iran stock': 'سهام ایران', 'Global stock': 'سهام جهانی', 'Gold': 'طلا', 'Silver': 'نقره', 'Manual': 'دستی', 'Unit': 'واحد', 'Gram': 'گرم', 'Market': 'بازاری',
    'Buy': 'خرید', 'Sell': 'فروش', 'Name': 'نام', 'Asset type': 'نوع دارایی', 'Pricing mode': 'روش قیمت‌گذاری', 'Price provider': 'ارائه‌دهندهٔ قیمت', 'Manual price currency': 'ارز قیمت دستی', 'Is active': 'فعال است', 'Notes': 'یادداشت‌ها', 'Transaction type': 'نوع تراکنش', 'Price per unit': 'قیمت هر واحد', 'Fee': 'کارمزد', 'Transaction currency': 'ارز تراکنش', 'Executed at': 'زمان اجرا', 'Currency': 'ارز', 'Captured at': 'زمان ثبت',
    'Add asset': 'افزودن دارایی', 'Create asset': 'ایجاد دارایی', 'Edit asset': 'ویرایش دارایی', 'Save changes': 'ذخیرهٔ تغییرات', 'Record transaction': 'ثبت تراکنش', 'Edit transaction': 'ویرایش تراکنش', 'Save transaction': 'ذخیرهٔ تراکنش', 'Save price': 'ذخیرهٔ قیمت',
}


def translate(value):
    """Translate known interface text when Persian is the selected locale."""
    text = str(value)
    return FA.get(text, text) if (get_language() or '').lower().startswith('fa') else text
