#!/usr/bin/env python3
"""
generate_varied_synthetic_data.py — Varied Synthetic NER Training Data Generator
================================================================================
Generates realistic Indian bank transaction SMS messages with NER annotations
for training the Finlight NER model. Creates highly varied examples covering:
- Mainstream banks, Neo-banks, Co-operative banks, Rural banks.
- International services, local merchants, standard utility merchants.
- Success, Failed, Refunds, Returns, Auto-sweep, and EMI transactions.
- Structural noise like date variations, spelling variations, unusual spacings.

Usage:
    python generate_varied_synthetic_data.py --output data/synthetic_varied_ner_data.json --count 1000
"""

import argparse
import json
import random
import re
import string
import uuid
from typing import List, Dict, Tuple, Optional


# ============================================================================
# Data Pools
# ============================================================================

BANKS_FULL = [
    "HDFC Bank", "ICICI Bank", "State Bank of India", "Axis Bank",
    "Kotak Mahindra Bank", "IndusInd Bank", "Yes Bank", "IDBI Bank",
    "Federal Bank", "South Indian Bank", "RBL Bank", "Bandhan Bank",
    "IDFC First Bank", "Punjab National Bank", "Bank of Baroda",
    "Canara Bank", "Union Bank of India", "Bank of India", "Indian Bank",
    "Central Bank of India", "Bank of Maharashtra", "UCO Bank",
    "Karnataka Bank", "Karur Vysya Bank", "City Union Bank",
    "AU Small Finance Bank", "Paytm Payments Bank", "DBS Bank",
    "Standard Chartered Bank", "HSBC Bank", "Citibank",
    # Additional Variety
    "Saraswat Bank", "SVC Co-operative Bank", "TJSB Bank", "Cosmos Bank",
    "Kerala Gramin Bank", "Andhra Pradesh Grameena Vikas Bank",
    "Fi Money", "Jupiter", "Niyo", "Freo", "Airtel Payments Bank",
    "Equitas Small Finance Bank", "Ujjivan Small Finance Bank"
]

BANKS_SHORT = {
    "HDFC Bank": "HDFC", "ICICI Bank": "ICICI", "State Bank of India": "SBI",
    "Axis Bank": "AxisBk", "Kotak Mahindra Bank": "Kotak",
    "IndusInd Bank": "IndusInd", "Yes Bank": "YesBk", "IDBI Bank": "IDBI",
    "Federal Bank": "FedBk", "Punjab National Bank": "PNB",
    "Bank of Baroda": "BOB", "Canara Bank": "Canara",
    "Union Bank of India": "UBI", "Bank of India": "BOI",
    "Indian Bank": "IndianBk", "Central Bank of India": "CBI",
    "Bank of Maharashtra": "BOM", "UCO Bank": "UCO",
    "Karnataka Bank": "KBL", "Bandhan Bank": "Bandhan",
    "AU Small Finance Bank": "AUSFB", "Paytm Payments Bank": "PaytmBk",
    "DBS Bank": "DBS", "Standard Chartered Bank": "StanChart",
    "HSBC Bank": "HSBC", "Citibank": "Citi", "RBL Bank": "RBL",
    "IDFC First Bank": "IDFC", "South Indian Bank": "SIB",
    "Karur Vysya Bank": "KVB", "City Union Bank": "CUB",
    "Saraswat Bank": "Saraswat", "SVC Co-operative Bank": "SVC",
    "TJSB Bank": "TJSB", "Cosmos Bank": "Cosmos",
    "Kerala Gramin Bank": "KGB", "Andhra Pradesh Grameena Vikas Bank": "APGVB",
    "Fi Money": "Fi", "Jupiter": "Jupiter", "Niyo": "Niyo", "Freo": "Freo",
    "Airtel Payments Bank": "AirtelBk", "Equitas Small Finance Bank": "Equitas",
    "Ujjivan Small Finance Bank": "Ujjivan"
}

MERCHANTS_ECOMMERCE = [
    "Amazon", "Flipkart", "Myntra", "Ajio", "Meesho", "Nykaa",
    "Tata CLiQ", "Snapdeal", "JioMart", "BigBasket", "Blinkit",
    "Zepto", "DMart Ready", "Croma", "Reliance Digital",
]

MERCHANTS_FOOD = [
    "Swiggy", "Zomato", "Dominos", "McDonalds", "KFC", "Pizza Hut",
    "Burger King", "Starbucks", "CCD", "Subway", "Haldirams",
    "Barbeque Nation", "Chai Point", "Chaayos", "Box8",
]

MERCHANTS_UTILITY = [
    "Jio Prepaid", "Airtel Recharge", "Vi Recharge", "BSNL",
    "Tata Power", "Adani Electricity", "BESCOM", "MSEDCL",
    "Mahanagar Gas", "IGL", "Hathway", "ACT Fibernet",
    "BWSSB Water", "MTNL", "DishTV",
]

MERCHANTS_TRAVEL = [
    "IRCTC", "MakeMyTrip", "Goibibo", "Yatra", "Cleartrip",
    "Ola", "Uber", "Rapido", "IndiGo", "Air India", "SpiceJet",
    "RedBus", "AbhiBus", "EaseMyTrip", "ixigo", "Namma Yatri", "MakeMyTrip India"
]

MERCHANTS_FINANCE = [
    "LIC Premium", "SBI Life", "HDFC Life", "Max Life",
    "Bajaj Finserv", "Tata AIG", "ICICI Prudential",
    "SBI Mutual Fund", "Zerodha", "Groww", "Paytm Money",
    "PhonePe", "Google Pay", "CRED", "Upstox", "Kuvera"
]

MERCHANTS_SHOPPING = [
    "Reliance Trends", "Shoppers Stop", "Lifestyle", "Westside",
    "Decathlon", "IKEA", "HomeCentre", "Pepperfry",
    "Urban Ladder", "Lenskart", "Titan Eye Plus",
    "Croma Electronics", "Vijay Sales", "Poorvika Mobiles",
]

MERCHANTS_HEALTH = [
    "Apollo Pharmacy", "MedPlus", "Netmeds", "PharmEasy", "1mg",
    "Apollo Hospital", "Fortis Hospital", "Max Hospital",
    "Manipal Hospital", "Practo", "Cult Fit",
]

MERCHANTS_EDUCATION = [
    "Byju's", "Unacademy", "Vedantu", "upGrad", "Simplilearn",
    "Coursera", "Udemy", "WhiteHat Jr", "Physics Wallah", "Khan Academy"
]

MERCHANTS_INTERNATIONAL = [
    "AWS", "DigitalOcean", "Netflix", "Spotify", "Steam",
    "PlayStation", "Xbox", "Apple Services", "Google Cloud",
    "Meta Ads", "ChatGPT", "Midjourney", "GitHub", "Vercel", "Heroku"
]

MERCHANTS_LOCAL = [
    "Sri Balaji Stores", "Sharma Kirana", "Gupta Sweets", "A2B",
    "Adyar Ananda Bhavan", "Local Medicals", "XYZ Mart",
    "Bikanerwala", "Haldiram's Outlet", "Vinayaka Provision",
    "Royal Enfield Service", "Mani Tyre Shop", "Raju Veggies"
]

PERSON_FIRST_NAMES = [
    "Rahul", "Priya", "Amit", "Sneha", "Vikram", "Ananya", "Suresh",
    "Deepa", "Rajesh", "Kavita", "Arun", "Meera", "Sanjay", "Pooja",
    "Manoj", "Divya", "Kiran", "Neha", "Ashok", "Lakshmi", "Ravi",
    "Sunita", "Vijay", "Anjali", "Prakash", "Swati", "Dinesh", "Nisha",
    "Ramesh", "Geeta", "Mohan", "Asha", "Sunil", "Rekha", "Harish",
]

PERSON_LAST_NAMES = [
    "Sharma", "Verma", "Gupta", "Singh", "Kumar", "Patel", "Reddy",
    "Nair", "Pillai", "Iyer", "Rao", "Desai", "Joshi", "Kulkarni",
    "Bhat", "Hegde", "Shetty", "Menon", "Das", "Chatterjee", "Banerjee",
    "Mukherjee", "Agarwal", "Jain", "Mehta", "Shah", "Trivedi",
    "Mishra", "Pandey", "Dubey", "Tiwari", "Yadav", "Chauhan",
]

UPI_SUFFIXES = [
    "@ybl", "@paytm", "@oksbi", "@okicici", "@okaxis", "@okhdfcbank",
    "@upi", "@apl", "@ikwik", "@freecharge", "@ibl", "@axl",
    "@jupiteraxis", "@slice", "@kotak", "@federal", "@postbank", "@fbl"
]

ALL_MERCHANTS = (
    MERCHANTS_ECOMMERCE + MERCHANTS_FOOD + MERCHANTS_UTILITY +
    MERCHANTS_TRAVEL + MERCHANTS_FINANCE + MERCHANTS_SHOPPING +
    MERCHANTS_HEALTH + MERCHANTS_EDUCATION + MERCHANTS_INTERNATIONAL +
    MERCHANTS_LOCAL
)


# ============================================================================
# Helper Functions
# ============================================================================

def random_amount(low=10, high=500000) -> float:
    """Generate a realistic transaction amount."""
    r = random.random()
    if r < 0.3:
        return round(random.uniform(10, 500), 2)
    elif r < 0.6:
        return round(random.uniform(500, 5000), 2)
    elif r < 0.8:
        return round(random.uniform(5000, 50000), 2)
    else:
        return round(random.uniform(50000, high), 2)


def format_amount(amt: float, noise: bool = False) -> str:
    """Format amount in various Indian styles, optionally adding noise."""
    styles = ["rs_dot", "rs_space", "inr_space", "inr_comma", "rs_slash", "rupee"]
    if noise:
        styles += ["rs_space_dot_space", "inr_dot", "rs_dash"]
        
    style = random.choice(styles)
    
    if style == "rs_dot":
        if random.random() < 0.5 and amt == int(amt):
            return f"Rs.{int(amt)}"
        return f"Rs.{amt:,.2f}"
    elif style == "rs_space":
        if random.random() < 0.5 and amt == int(amt):
            return f"Rs {int(amt)}"
        return f"Rs {amt:,.2f}"
    elif style == "inr_space":
        return f"INR {amt:,.2f}"
    elif style == "inr_comma":
        return f"INR {amt:,.2f}"
    elif style == "rs_slash":
        return f"Rs.{amt:,.2f}/-"
    elif style == "rs_space_dot_space":
        return f"Rs . {amt:,.2f}"
    elif style == "inr_dot":
        return f"INR.{amt:,.2f}"
    elif style == "rs_dash":
        return f"Rs-{amt:,.2f}"
    else:
        return f"Rs.{amt:,.2f}"


def format_balance(amt: float, noise: bool = False) -> str:
    """Format balance amount, handling standard and noisy variations."""
    styles = ["avl_bal_rs", "avl_bal_inr", "bal_rs", "available", "avlbl", "cr_bal"]
    if noise:
        styles += ["avail_bal", "aval_bal", "bal_dot", "bal_space_rs"]
        
    style = random.choice(styles)
    
    if style == "avl_bal_rs":
        return f"Avl Bal Rs.{amt:,.2f}"
    elif style == "avl_bal_inr":
        return f"Avl Bal INR {amt:,.2f}"
    elif style == "bal_rs":
        return f"Bal:Rs.{amt:,.2f}"
    elif style == "available":
        return f"Available Balance INR {amt:,.2f}"
    elif style == "avlbl":
        return f"Avlbl Amt:Rs.{amt:,.2f}"
    elif style == "cr_bal":
        return f"Total Bal:Rs.{amt:,.2f}CR"
    elif style == "avail_bal":
        return f"Avail bal Rs.{amt:,.2f}"
    elif style == "aval_bal":
        return f"Aval Bal INR {amt:,.2f}"
    elif style == "bal_dot":
        return f"Bal. Rs.{amt:,.2f}"
    elif style == "bal_space_rs":
        return f"Bal Rs {amt:,.2f}"
        
    return f"Avl Bal Rs {amt:,.2f}"


def format_acct_prefix(noise: bool = False) -> str:
    """Generate account prefix variations."""
    if noise:
        return random.choice(["A/c", "A/C", "Acct", "Ac", "Acc", "A/c.", "Account", "a/c"])
    return random.choice(["A/c", "A/C", "Acct"])

def random_acct_number() -> str:
    """Generate a masked account number."""
    style = random.choice(["xx4", "xx6", "star4", "star6", "dots4", "full_masked"])
    last = ''.join(random.choices(string.digits, k=random.choice([4, 6])))
    
    if style == "xx4":
        return f"XX{last[:4]}"
    elif style == "xx6":
        return f"XXXXX{last}"
    elif style == "star4":
        return f"**{last[:4]}"
    elif style == "star6":
        return f"***{last}"
    elif style == "dots4":
        return f"...{last[:4]}"
    else:
        return f"XXXXXX{last[:4]}"


def random_date(noise: bool = False) -> str:
    """Generate a random date string with optional noisy variations like spelt-out months."""
    d = random.randint(1, 28)
    m = random.randint(1, 12)
    y = random.choice([2023, 2024, 2025, 2026])
    
    styles = ["dd-mm-yy", "dd/mm/yy", "dd-mm-yyyy", "yyyy-mm-dd"]
    if noise:
        styles += ["dd-Mon-yy", "dd Mon yyyy", "yyyy.mm.dd"]
        
    style = random.choice(styles)
    mon = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"][m-1]

    if style == "dd-mm-yy":
        return f"{d:02d}-{m:02d}-{y%100:02d}"
    elif style == "dd/mm/yy":
        return f"{d:02d}/{m:02d}/{y%100:02d}"
    elif style == "dd-mm-yyyy":
        return f"{d:02d}-{m:02d}-{y}"
    elif style == "yyyy-mm-dd":
        return f"{y}-{m:02d}-{d:02d}"
    elif style == "dd-Mon-yy":
        return f"{d:02d}-{mon}-{y%100:02d}"
    elif style == "dd Mon yyyy":
        return f"{d:02d} {mon} {y}"
    elif style == "yyyy.mm.dd":
        return f"{y}.{m:02d}.{d:02d}"
    else:
        return f"{d:02d}-{m:02d}-{y}"


def random_ref_no() -> str:
    """Generate a random reference number."""
    return ''.join(random.choices(string.digits, k=random.randint(10, 14)))


def random_merchant() -> str:
    """Pick a random merchant/payee including person names."""
    r = random.random()
    if r < 0.15:
        # Person name (P2P transfer)
        first = random.choice(PERSON_FIRST_NAMES)
        last = random.choice(PERSON_LAST_NAMES)
        style = random.choice(["full", "initial", "mr", "upi_id"])
        if style == "full":
            return f"{first} {last}"
        elif style == "initial":
            return f"{first} {last[0]}"
        elif style == "mr":
            return f"Mr {first} {last}"
        else:
            upi = f"{first.lower()}{random.randint(1,99)}{random.choice(UPI_SUFFIXES)}"
            return upi
    elif r < 0.25:
        # UPI ID style
        return f"{random.choice(PERSON_FIRST_NAMES).lower()}{random.randint(100,9999)}{random.choice(UPI_SUFFIXES)}"
    elif r < 0.35:
        # Phone number (P2P)
        return f"{random.choice(['9', '8', '7', '6'])}{random.randint(100000000, 999999999)}"
    else:
        return random.choice(ALL_MERCHANTS)


def random_bank() -> Tuple[str, str]:
    """Return (full_name, short_name) for a random bank."""
    bank = random.choice(BANKS_FULL)
    short = BANKS_SHORT.get(bank, bank.split()[0])
    return bank, short


def tokenize(text: str) -> List[str]:
    """
    Replicate the Kotlin tokenizer logic:
    Split on whitespace, then split mixed tokens on separators like - / : . etc.
    at word boundaries (letter-to-digit, digit-to-letter transitions, and certain punctuation).
    """
    tokens = []
    words = text.split()
    
    for word in words:
        if not word:
            continue
        subtokens = _split_mixed_token(word)
        tokens.extend(subtokens)
    
    return [t for t in tokens if t.strip()]


def _split_mixed_token(token: str) -> List[str]:
    """Split a token on separators and type transitions, mimicking Kotlin logic."""
    keep_patterns = [
        r'^Rs\.[\d,]+\.?\d*/?-?$',
        r'^INR$',
        r'^A/[cC]\.?$',
        r'^No\.\d+\.?$',
        r'^XXXXX?\d+$',
        r'^\*\*\d+$',
        r'^\.\.\.\d+$',
        r'^\.\d+$',
        r'^INR\.[\d,]+\.?\d*$',
        r'^Rs-[\d,]+\.?\d*$'
    ]
    
    for pat in keep_patterns:
        if re.match(pat, token, re.IGNORECASE):
            return [token]
    
    parts = re.split(r'(?<=\d)-(?=[A-Za-z])|(?<=[A-Za-z])-(?=\d)|(?<=[A-Za-z])-(?=[A-Za-z])', token)
    
    result = []
    for part in parts:
        if part:
            if '/' in part and not re.match(r'^[A-Za-z]/[A-Za-z]\.?$', part, re.IGNORECASE):
                subparts = part.split('/')
                result.extend([s for s in subparts if s])
            else:
                result.append(part)
    
    return result if result else [token]


def find_token_indices(tokens: List[str], search_tokens: List[str]) -> List[int]:
    """Find indices of search_tokens within tokens list."""
    if not search_tokens:
        return []
    
    for i in range(len(tokens) - len(search_tokens) + 1):
        match = True
        for j, st in enumerate(search_tokens):
            if tokens[i + j].lower().rstrip('.,;:') != st.lower().rstrip('.,;:'):
                match = False
                break
        if match:
            return list(range(i, i + len(search_tokens)))
    
    # Fallback
    indices = []
    for st in search_tokens:
        for i, t in enumerate(tokens):
            if t.lower().rstrip('.,;:') == st.lower().rstrip('.,;:') and i not in indices:
                indices.append(i)
                break
    return indices


def make_id() -> str:
    """Generate a unique ID similar to the existing format."""
    return str(random.randint(1600000000000, 1700000000000))


# ============================================================================
# SMS Template Generators
# ============================================================================

def gen_generic_debit(noise: bool = False) -> dict:
    bank_full, bank_short = random_bank()
    merchant = random_merchant()
    amt = random_amount()
    acct = random_acct_number()
    acct_prefix = format_acct_prefix(noise)
    bal = random_amount(1000, 1000000)
    date = random_date(noise)
    amt_str = format_amount(amt, noise)
    bal_str = format_balance(bal, noise)
    
    templates = [
        f"Alert: {amt_str} debited from {acct_prefix} {acct} on {date} to {merchant}. {bal_str}. If not done by you call 18002586-{bank_short}",
        f"{bank_short}: {amt_str} has been debited from your {acct_prefix} ending {acct} for {merchant} on {date}. {bal_str}",
        f"Money Debited! {amt_str} spent on {merchant} using {bank_short} {acct_prefix} {acct} on {date}. {bal_str}",
        f"Dear Customer, {amt_str} is debited from {acct_prefix} {acct} for {merchant} via NetBanking. {bal_str}-{bank_short}",
    ]
    return _build_item(random.choice(templates), merchant, amt_str, acct, bank_short, bal_str)


def gen_generic_credit(noise: bool = False) -> dict:
    bank_full, bank_short = random_bank()
    merchant = random_merchant()
    amt = random_amount()
    acct = random_acct_number()
    acct_prefix = format_acct_prefix(noise)
    bal = random_amount(1000, 1000000)
    date = random_date(noise)
    amt_str = format_amount(amt, noise)
    bal_str = format_balance(bal, noise)
    
    templates = [
        f"UPDATE: Your {acct_prefix} {acct} credited with {amt_str} on {date} by {merchant}. {bal_str}-{bank_short}",
        f"{bank_short}: {amt_str} credited to your {acct_prefix} {acct} on {date}. Info: {merchant}. {bal_str}",
        f"Dear Customer, {amt_str} credited to your {acct_prefix} ending {acct} from {merchant} on {date}. {bal_str}-{bank_short}",
    ]
    return _build_item(random.choice(templates), merchant, None, acct, bank_short, bal_str, is_credit=True)


def gen_refund(noise: bool = False) -> dict:
    """Refunds SMS scenario."""
    bank_full, bank_short = random_bank()
    merchant = random_merchant()
    amt = random_amount()
    acct = random_acct_number()
    acct_prefix = format_acct_prefix(noise)
    bal = random_amount(1000, 1000000)
    date = random_date(noise)
    amt_str = format_amount(amt, noise)
    bal_str = format_balance(bal, noise)
    
    templates = [
        f"Refund: {amt_str} processed by {merchant} has been credited to {acct_prefix} {acct} on {date}. {bal_str}. {bank_short}",
        f"{bank_short}: Your refund of {amt_str} from {merchant} is successful. Credited to {acct_prefix} {acct}. {bal_str}",
        f"Reversal of {amt_str} for failed txn at {merchant} is credited to {acct_prefix} {acct}. {bal_str}-{bank_short}",
    ]
    return _build_item(random.choice(templates), merchant, None, acct, bank_short, bal_str, is_credit=True)


def gen_failed_txn(noise: bool = False) -> dict:
    """Failed transaction SMS scenario (often excludes balance updates)."""
    bank_full, bank_short = random_bank()
    merchant = random_merchant()
    amt = random_amount()
    acct = random_acct_number()
    acct_prefix = format_acct_prefix(noise)
    date = random_date(noise)
    amt_str = format_amount(amt, noise)
    
    templates = [
        f"Alert: Txn of {amt_str} using {acct_prefix} {acct} at {merchant} on {date} has failed. {bank_short}",
        f"Declined: {amt_str} for {merchant} at {date} using your {bank_short} {acct_prefix} {acct}. Insufficient funds.",
        f"{bank_short}: Your payment of {amt_str} to {merchant} failed on {date}. A/c {acct} not debited.",
    ]
    # No balance
    return _build_item(random.choice(templates), merchant, None, acct, bank_short, None)


def gen_auto_sweep(noise: bool = False) -> dict:
    """Auto-sweep / FD creation SMS mapping to internal account but acting like a debit."""
    bank_full, bank_short = random_bank()
    merchant = "Auto Sweep FD"
    amt = random_amount(10000, 500000)
    acct = random_acct_number()
    acct_prefix = format_acct_prefix(noise)
    bal = random_amount(5000, 50000)
    date = random_date(noise)
    amt_str = format_amount(amt, noise)
    bal_str = format_balance(bal, noise)
    
    templates = [
        f"{bank_short}: {amt_str} swept out from {acct_prefix} {acct} to {merchant} on {date}. {bal_str}",
        f"{amt_str} debited from your {acct_prefix} {acct} towards {merchant} on {date}. {bal_str}. {bank_short}",
    ]
    return _build_item(random.choice(templates), merchant, None, acct, bank_short, bal_str)


def gen_upi_generic(noise: bool = False) -> dict:
    """Generic UPI transaction SMS."""
    bank_full, bank_short = random_bank()
    merchant = random_merchant()
    amt = random_amount(1, 100000)
    acct = random_acct_number()
    acct_prefix = format_acct_prefix(noise)
    bal = random_amount(100, 500000)
    date = random_date(noise)
    ref = random_ref_no()
    debited = random.random() < 0.5
    amt_str = format_amount(amt, noise)
    bal_str = format_balance(bal, noise)
    
    templates = [
        f"{amt_str} {'debited' if debited else 'credited'} {'from' if debited else 'to'} {acct_prefix} {acct} {'to' if debited else 'from'} {merchant} via UPI. Ref: {ref}. {bal_str}-{bank_short}",
        f"UPI txn: {amt_str} {'sent to' if debited else 'received from'} {merchant} from {acct_prefix} {acct}. UPI Ref {ref}. {bank_short}",
        f"{bank_short}: {amt_str} {'paid to' if debited else 'received from'} {merchant} on {date} via UPI. {acct_prefix} {acct}. {bal_str}",
    ]
    return _build_item(random.choice(templates), merchant, None, acct, bank_short, None, is_credit=not debited)


def gen_neft_imps(noise: bool = False) -> dict:
    """NEFT/IMPS transaction SMS."""
    bank_full, bank_short = random_bank()
    merchant = random_merchant()
    amt = random_amount(100, 500000)
    acct = random_acct_number()
    acct_prefix = format_acct_prefix(noise)
    bal = random_amount(1000, 1000000)
    date = random_date(noise)
    ref = random_ref_no()
    mode = random.choice(["NEFT", "IMPS", "RTGS"])
    credited = random.random() < 0.5
    amt_str = format_amount(amt, noise)
    bal_str = format_balance(bal, noise)
    
    templates = [
        f"{bank_short}: {amt_str} {'credited to' if credited else 'debited from'} {acct_prefix} {acct} via {mode} on {date}. {'From' if credited else 'To'}: {merchant}. Ref: {ref}. {bal_str}",
        f"{amt_str} {'received' if credited else 'transferred'} {'from' if credited else 'to'} {merchant} via {mode}. {acct_prefix} {acct}. {bal_str}. {bank_short}",
    ]
    return _build_item(random.choice(templates), merchant, None, acct, bank_short, None, is_credit=credited)


def gen_pos_transaction(noise: bool = False) -> dict:
    """POS (Point of Sale) card swipe transaction including Neo-banks."""
    bank_full, bank_short = random_bank()
    merchant = random.choice(ALL_MERCHANTS)
    amt = random_amount(50, 50000)
    acct = random_acct_number()
    bal = random_amount(100, 500000)
    date = random_date(noise)
    card_type = random.choice(["Debit Card", "Credit Card", "Forex Card"])
    amt_str = format_amount(amt, noise)
    bal_str = format_balance(bal, noise)
    
    templates = [
        f"{bank_short}: {amt_str} spent on your {card_type} {acct} at {merchant} on {date}. {bal_str}",
        f"{amt_str} debited from {bank_short} {card_type} ending {acct} at POS-{merchant} on {date}. {bal_str}",
        f"Alert! {amt_str} spent at {merchant} using {bank_short} Card {acct} on {date}. {bal_str}. Not you? Call 18001234",
    ]
    return _build_item(random.choice(templates), merchant, None, acct, bank_short, None)


def gen_wallet_txn(noise: bool = False) -> dict:
    """Digital wallet / fintech transaction."""
    wallet = random.choice(["Paytm", "PhonePe", "Google Pay", "Amazon Pay", "MobiKwik", "Cred Pay"])
    merchant = random.choice(ALL_MERCHANTS)
    amt = random_amount(1, 10000)
    date = random_date(noise)
    ref = random_ref_no()
    amt_str = format_amount(amt, noise)
    
    templates = [
        f"{wallet}: {amt_str} paid to {merchant} on {date}. Txn ID: {ref}",
        f"Payment of {amt_str} to {merchant} via {wallet} successful. Ref: {ref}. Date: {date}",
        f"{amt_str} debited from {wallet} wallet for {merchant} on {date}. Ref {ref}",
    ]
    text = random.choice(templates)
    tokens = tokenize(text)
    
    merchant_tokens = tokenize(merchant)
    merchant_idx = find_token_indices(tokens, merchant_tokens)
    
    amt_tokens = tokenize(amt_str)
    amt_idx = find_token_indices(tokens, amt_tokens)
    if not amt_idx and " " in amt_str:
        amt_idx = _find_amount_tokens(tokens)
    
    entities = []
    if merchant_idx:
        entities.append({
            "type": "MERCHANT",
            "tokenIndices": merchant_idx,
            "text": " ".join(tokens[i] for i in merchant_idx)
        })
    if amt_idx:
        entities.append({
            "type": "AMOUNT",
            "tokenIndices": amt_idx,
            "text": " ".join(tokens[i] for i in amt_idx)
        })
    
    return _build_training_item(text, tokens, entities)


# ============================================================================
# Core Builder
# ============================================================================

def _build_item(text: str, merchant: str, amt_str_override: Optional[str],
                acct: str, bank_short: str, bal_str_override: Optional[str],
                is_credit: bool = False) -> dict:
    """Build a training item from SMS text and known entity values."""
    tokens = tokenize(text)
    entities = []
    
    # Find MERCHANT
    merchant_tokens = tokenize(merchant)
    merchant_idx = find_token_indices(tokens, merchant_tokens)
    if merchant_idx:
        entities.append({
            "type": "MERCHANT",
            "tokenIndices": merchant_idx,
            "text": " ".join(tokens[i] for i in merchant_idx)
        })
    
    # Find AMOUNT - search for amount patterns in the text
    amt_idx = _find_amount_tokens(tokens)
    if amt_idx:
        entities.append({
            "type": "AMOUNT",
            "tokenIndices": amt_idx,
            "text": " ".join(tokens[i] for i in amt_idx)
        })
    
    # Find ACCOUNT - look for account number patterns
    acct_tokens = tokenize(acct)
    acct_idx = find_token_indices(tokens, acct_tokens)
    # Also try to include bank name and A/c prefix
    bank_tokens = tokenize(bank_short)
    bank_idx = find_token_indices(tokens, bank_tokens)
    
    # Look for A/c or A/C prefix near account number
    ac_prefix_idx = []
    for i, t in enumerate(tokens):
        if t.lower() in ["a/c", "a/c.", "ac", "acct", "acc", "account"]:
            ac_prefix_idx = [i]
            break
    
    combined_acct = sorted(set(bank_idx + ac_prefix_idx + acct_idx))
    if combined_acct:
        entities.append({
            "type": "ACCOUNT",
            "tokenIndices": combined_acct,
            "text": " ".join(tokens[i] for i in combined_acct)
        })
    
    # Find BALANCE
    bal_idx = _find_balance_tokens(tokens)
    if bal_idx:
        entities.append({
            "type": "BALANCE",
            "tokenIndices": bal_idx,
            "text": " ".join(tokens[i] for i in bal_idx)
        })
    
    return _build_training_item(text, tokens, entities)


def _find_amount_tokens(tokens: List[str]) -> List[int]:
    """Find amount-related tokens."""
    for i, t in enumerate(tokens):
        # Pattern: "Rs.1500" or "Rs-1500"
        if re.match(r'^Rs[.-]?[\d,]+\.?\d*$', t, re.IGNORECASE) or re.match(r'^INR\.?[\d,]+\.?\d*$', t, re.IGNORECASE):
            return [i]
        
        # Pattern: "INR" or "Rs" followed by number
        if t.upper() in ["INR", "RS", "RS.", "INR.", "RS-"]:
            if i + 1 < len(tokens):
                nxt = tokens[i+1]
                if re.match(r'^[\d,]+\.?\d*$', nxt):
                    return [i, i+1]
                elif nxt == "." and i + 2 < len(tokens) and re.match(r'^[\d,]+\.?\d*$', tokens[i+2]):
                    return [i, i+1, i+2]
    return []


def _find_balance_tokens(tokens: List[str]) -> List[int]:
    """Find balance-related tokens."""
    for i, t in enumerate(tokens):
        lower = t.lower()
        # Look for "Avl Bal", "Bal:", "Balance", "Avlbl"
        if lower in ["avl", "avlbl", "available", "avail", "aval"]:
            # Collect balance context + amount
            idx = [i]
            j = i + 1
            while j < len(tokens) and j <= i + 4:
                tj = tokens[j].lower()
                if tj in ["bal", "bal.", "bal:", "balance", "amt", "amt:", "lmt", "lmt:"]:
                    idx.append(j)
                elif tj in ["inr", "rs", "rs.", "rs:"] or re.match(r'^Rs[\.:]\d', tokens[j], re.IGNORECASE):
                    idx.append(j)
                    # Check for following number
                    if j + 1 < len(tokens) and re.match(r'^[\d,]+\.?\d*$', tokens[j+1]):
                        idx.append(j + 1)
                    # Check for space separated .
                    elif j + 2 < len(tokens) and tokens[j+1] == "." and re.match(r'^[\d,]+\.?\d*$', tokens[j+2]):
                        idx.extend([j+1, j+2])
                    break
                elif re.match(r'^[\d,]+\.?\d*$', tokens[j]):
                    idx.append(j)
                    break
                else:
                    idx.append(j)
                j += 1
            if len(idx) >= 2:
                return idx
        
        # "Bal:Rs.1234" or "Bal Rs" pattern
        if lower.startswith("bal") and lower not in ["balance", "balances", "by"]:
            if re.match(r'^Bal[.:]?Rs\.[\d,]+\.?\d*', t, re.IGNORECASE):
                return [i]
            if i + 1 < len(tokens):
                nxt = tokens[i+1]
                if nxt.upper() in ["INR", "RS", "RS."] or re.match(r'^Rs\.[\d,]', nxt, re.IGNORECASE):
                    idx = [i, i+1]
                    if i + 2 < len(tokens) and re.match(r'^[\d,]+\.?\d*', tokens[i+2]):
                        idx.append(i + 2)
                    return idx
        
        # "Total Bal:" pattern
        if lower == "total" and i + 1 < len(tokens) and tokens[i+1].lower().startswith("bal"):
            idx = [i, i+1]
            j = i + 2
            if j < len(tokens) and (re.match(r'^Rs\.[\d,]', tokens[j], re.IGNORECASE) or tokens[j].upper() in ["INR", "RS"]):
                idx.append(j)
                if j + 1 < len(tokens) and re.match(r'^[\d,]+\.?\d*', tokens[j+1]):
                    idx.append(j + 1)
            return idx
    
    return []


def _build_training_item(text: str, tokens: List[str], entities: List[dict]) -> dict:
    """Build the final training item dict."""
    has_merchant = any(e["type"] == "MERCHANT" for e in entities)
    has_amount = any(e["type"] == "AMOUNT" for e in entities)
    has_account = any(e["type"] == "ACCOUNT" for e in entities)
    has_balance = any(e["type"] == "BALANCE" for e in entities)
    entity_count = sum([has_merchant, has_amount, has_account, has_balance])
    
    return {
        "id": make_id(),
        "text": text,
        "tokens": tokens,
        "entities": entities,
        "completeness": {
            "hasMerchant": has_merchant,
            "hasAmount": has_amount,
            "hasAccount": has_account,
            "hasBalance": has_balance,
            "qualityScore": entity_count / 4.0
        }
    }


# ============================================================================
# Main Generator
# ============================================================================

GENERATORS = [
    (gen_generic_debit, 8),
    (gen_generic_credit, 6),
    (gen_upi_generic, 10),
    (gen_neft_imps, 7),
    (gen_pos_transaction, 6),
    (gen_wallet_txn, 4),
    (gen_refund, 4),
    (gen_failed_txn, 3),
    (gen_auto_sweep, 2)
]


def generate_synthetic_data(count: int, seed: int = 42) -> List[dict]:
    """Generate N synthetic NER training items."""
    random.seed(seed)
    
    weighted_gens = []
    for gen_fn, weight in GENERATORS:
        weighted_gens.extend([gen_fn] * weight)
    
    items = []
    retries = 0
    max_retries = count * 3
    
    while len(items) < count and retries < max_retries:
        gen_fn = random.choice(weighted_gens)
        # Apply noise 20% of the time, allowing `gen_fn(noise=True)`
        noise = random.random() < 0.20
        try:
            item = gen_fn(noise=noise)
            # Validate: at least one entity was found
            if item["entities"]:
                items.append(item)
            else:
                retries += 1
        except Exception as e:
            retries += 1
            if retries % 100 == 0:
                print(f"  ⚠️ {retries} retries so far (last error: {e})")
    
    return items


def print_stats(items: List[dict]):
    """Print statistics about generated data."""
    total = len(items)
    merchant_count = sum(1 for i in items if i["completeness"]["hasMerchant"])
    amount_count = sum(1 for i in items if i["completeness"]["hasAmount"])
    account_count = sum(1 for i in items if i["completeness"]["hasAccount"])
    balance_count = sum(1 for i in items if i["completeness"]["hasBalance"])
    
    quality_scores = [i["completeness"]["qualityScore"] for i in items]
    avg_quality = sum(quality_scores) / max(len(quality_scores), 1)
    
    high_quality = sum(1 for s in quality_scores if s >= 0.75)
    complete = sum(1 for s in quality_scores if s >= 1.0)
    
    print(f"\n📊 Synthetic Data Statistics:")
    print(f"   Total items generated: {total}")
    print(f"   MERCHANT entities:     {merchant_count} ({100*merchant_count/total:.1f}%)")
    print(f"   AMOUNT entities:       {amount_count} ({100*amount_count/total:.1f}%)")
    print(f"   ACCOUNT entities:      {account_count} ({100*account_count/total:.1f}%)")
    print(f"   BALANCE entities:      {balance_count} ({100*balance_count/total:.1f}%)")
    print(f"   Avg quality score:     {avg_quality:.2f}")
    print(f"   High quality (≥0.75):  {high_quality} ({100*high_quality/total:.1f}%)")
    print(f"   Complete (4/4):        {complete} ({100*complete/total:.1f}%)")
    
    # Sample items
    print(f"\n📝 Sample items:")
    for item in random.sample(items, min(5, len(items))):
        print(f"   Text: {item['text'][:120]}...")
        for e in item["entities"]:
            print(f"      {e['type']}: \"{e['text']}\" (indices: {e['tokenIndices']})")
        print()


def main():
    parser = argparse.ArgumentParser(description="Generate varied synthetic NER training data")
    parser.add_argument("--output", default="data/synthetic_varied_ner_data.json",
                        help="Output JSON file path")
    parser.add_argument("--count", type=int, default=1000,
                        help="Number of synthetic items to generate (default: 1000)")
    parser.add_argument("--seed", type=int, default=42,
                        help="Random seed for reproducibility")
    args = parser.parse_args()
    
    print("=" * 60)
    print("Varied Synthetic NER Training Data Generator")
    print("=" * 60)
    
    print(f"\n⚙️  Generating {args.count} varied synthetic items (seed={args.seed})...")
    items = generate_synthetic_data(args.count, args.seed)
    
    print_stats(items)
    
    # Save
    import os
    output_path = args.output
    if not output_path.startswith("/"):
        script_dir = os.path.dirname(os.path.abspath(__file__))
        output_path = os.path.join(script_dir, output_path)
    
    os.makedirs(os.path.dirname(output_path), exist_ok=True)
    
    with open(output_path, "w", encoding="utf-8") as f:
        json.dump(items, f, indent=4, ensure_ascii=False)
    
    print(f"\n✅ Saved {len(items)} varied items to: {output_path}")
    print(f"   File size: {os.path.getsize(output_path) / 1024:.1f} KB")


if __name__ == "__main__":
    main()
