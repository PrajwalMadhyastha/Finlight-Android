#!/usr/bin/env python3
"""
generate_synthetic_data.py — Synthetic NER Training Data Generator
==================================================================
Generates realistic Indian bank transaction SMS messages with NER annotations
for training the Finlight NER model. Creates varied examples covering:
- Multiple banks (HDFC, ICICI, SBI, Axis, Kotak, BOB, Union, etc.)
- Transaction types (debit, credit, UPI, NEFT, IMPS, POS, ATM, etc.)
- Amount formats (Rs.1500, INR 1,500.00, Rs 1500/-, etc.)
- Account formats (XX1234, A/c XXXXX5678, ending 4545, etc.)
- Balance formats (Avl Bal, Available Balance, Bal:, etc.)
- Merchant categories (e-commerce, food, utilities, persons, etc.)

Usage:
    python generate_synthetic_data.py --output data/synthetic_ner_data.json --count 500
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
    "RedBus", "AbhiBus", "EaseMyTrip", "ixigo",
]

MERCHANTS_FINANCE = [
    "LIC Premium", "SBI Life", "HDFC Life", "Max Life",
    "Bajaj Finserv", "Tata AIG", "ICICI Prudential",
    "SBI Mutual Fund", "Zerodha", "Groww", "Paytm Money",
    "PhonePe", "Google Pay", "CRED",
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
    "Coursera", "Udemy", "WhiteHat Jr",
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
    "@jupiteraxis", "@slice", "@kotak", "@federal",
]

ALL_MERCHANTS = (
    MERCHANTS_ECOMMERCE + MERCHANTS_FOOD + MERCHANTS_UTILITY +
    MERCHANTS_TRAVEL + MERCHANTS_FINANCE + MERCHANTS_SHOPPING +
    MERCHANTS_HEALTH + MERCHANTS_EDUCATION
)


# ============================================================================
# Helper Functions
# ============================================================================

def random_amount(low=10, high=500000) -> float:
    """Generate a realistic transaction amount."""
    # Weighted towards common transaction ranges
    r = random.random()
    if r < 0.3:
        return round(random.uniform(10, 500), 2)
    elif r < 0.6:
        return round(random.uniform(500, 5000), 2)
    elif r < 0.8:
        return round(random.uniform(5000, 50000), 2)
    else:
        return round(random.uniform(50000, high), 2)


def format_amount(amt: float) -> str:
    """Format amount in various Indian styles."""
    style = random.choice(["rs_dot", "rs_space", "inr_space", "inr_comma", "rs_slash", "rupee"])
    
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
    else:
        return f"Rs.{amt:,.2f}"


def format_balance(amt: float) -> str:
    """Format balance amount."""
    style = random.choice(["avl_bal_rs", "avl_bal_inr", "bal_rs", "available", "avlbl", "cr_bal"])
    
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
    return f"Avl Bal Rs {amt:,.2f}"


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


def random_date() -> str:
    """Generate a random date string."""
    d = random.randint(1, 28)
    m = random.randint(1, 12)
    y = random.choice([2023, 2024, 2025, 2026])
    style = random.choice(["dd-mm-yy", "dd/mm/yy", "dd-mm-yyyy", "yyyy-mm-dd"])
    
    if style == "dd-mm-yy":
        return f"{d:02d}-{m:02d}-{y%100:02d}"
    elif style == "dd/mm/yy":
        return f"{d:02d}/{m:02d}/{y%100:02d}"
    elif style == "dd-mm-yyyy":
        return f"{d:02d}-{m:02d}-{y}"
    else:
        return f"{y}-{m:02d}-{d:02d}"


def random_time() -> str:
    """Generate a random time string."""
    h = random.randint(0, 23)
    m = random.randint(0, 59)
    s = random.randint(0, 59)
    return f"{h:02d}:{m:02d}:{s:02d}"


def random_ref_no() -> str:
    """Generate a random reference number."""
    return ''.join(random.choices(string.digits, k=random.randint(10, 14)))


def random_merchant() -> str:
    """Pick a random merchant/payee."""
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
    # Split on hyphens, forward slashes, colons that act as separators
    # But preserve things like "Rs.1500" as ["Rs.1500"], "A/c" as ["A/c"]
    # and "1,95,011.73-SBI" as ["1,95,011.73", "SBI"]
    
    # Special patterns to keep together
    keep_patterns = [
        r'^Rs\.[\d,]+\.?\d*/?-?$',  # Rs.1500, Rs.1,500.00
        r'^INR$',
        r'^A/[cC]$',  # A/c, A/C
        r'^No\.\d+\.?$',  # No.518551.
        r'^XXXXX?\d+$',
        r'^\*\*\d+$',
        r'^\.\.\.\d+$',
        r'^\.\d+$',
    ]
    
    for pat in keep_patterns:
        if re.match(pat, token):
            return [token]
    
    # Split on dash between word and word (e.g., "100.00-SBI" -> ["100.00", "SBI"])
    parts = re.split(r'(?<=\d)-(?=[A-Za-z])|(?<=[A-Za-z])-(?=\d)|(?<=[A-Za-z])-(?=[A-Za-z])', token)
    
    result = []
    for part in parts:
        if part:
            # Further split on / but preserve A/c
            if '/' in part and not re.match(r'^[A-Za-z]/[a-z]$', part):
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
    
    # Fallback: try individual token matching
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

def gen_hdfc_debit() -> dict:
    bank_full, bank_short = "HDFC Bank", "HDFC"
    merchant = random_merchant()
    amt = random_amount()
    acct = random_acct_number()
    bal = random_amount(1000, 1000000)
    date = random_date()
    amt_str = format_amount(amt)
    bal_str = format_balance(bal)
    
    templates = [
        f"Alert: {amt_str} debited from A/c {acct} on {date} to {merchant}. {bal_str}. If not done by you call 18002586161-HDFC Bank",
        f"HDFC Bank: Rs.{amt:,.2f} has been debited from your A/c ending {acct} for {merchant} on {date}. Avl Bal: INR {bal:,.2f}",
        f"Money Debited! {amt_str} spent on {merchant} using HDFC Bank A/c {acct} on {date}. {bal_str}",
        f"Dear Customer, {amt_str} is debited from A/c {acct} for {merchant} via NetBanking. {bal_str}-HDFC Bank",
    ]
    return _build_item(random.choice(templates), merchant, amt_str, acct, bank_short, bal_str)


def gen_hdfc_credit() -> dict:
    merchant = random_merchant()
    amt = random_amount()
    acct = random_acct_number()
    bal = random_amount(1000, 1000000)
    date = random_date()
    amt_str = format_amount(amt)
    bal_str = format_balance(bal)
    
    templates = [
        f"UPDATE: Your A/c {acct} credited with INR {amt:,.2f} on {date} by {merchant}. {bal_str}-HDFC Bank",
        f"HDFC Bank: INR {amt:,.2f} credited to your A/c {acct} on {date}. Info: {merchant}. {bal_str}",
        f"Dear Customer, Rs.{amt:,.2f} credited to your A/c ending {acct} from {merchant} on {date}. {bal_str}-HDFC Bank",
    ]
    return _build_item(random.choice(templates), merchant, None, acct, "HDFC", bal_str, is_credit=True)


def gen_sbi_debit() -> dict:
    merchant = random_merchant()
    amt = random_amount()
    acct = random_acct_number()
    bal = random_amount(1000, 1000000)
    date = random_date()
    amt_str = format_amount(amt)
    bal_str = format_balance(bal)
    
    templates = [
        f"Your A/C {acct} Debited INR {amt:,.2f} on {date} -Transferred to {merchant}. Avl Balance INR {bal:,.2f}-SBI",
        f"Dear Customer, Your A/C ending with {acct} has been debited for INR {amt:,.2f} on {date} towards {merchant}",
        f"SBI: Rs.{amt:,.2f} debited from A/c {acct} on {date} by {merchant}. Avl Bal Rs.{bal:,.2f}. Not you? Call 18001111109",
        f"Your AC {acct} Debited INR {amt:,.2f} on {date} -Towards {merchant}. Avl Bal INR {bal:,.2f}-SBI",
    ]
    return _build_item(random.choice(templates), merchant, amt_str, acct, "SBI", bal_str)


def gen_sbi_credit() -> dict:
    merchant = random_merchant()
    amt = random_amount()
    acct = random_acct_number()
    bal = random_amount(1000, 1000000)
    date = random_date()
    amt_str = format_amount(amt)
    bal_str = format_balance(bal)
    
    templates = [
        f"Your A/C {acct} Credited INR {amt:,.2f} on {date} by {merchant}. Avl Balance INR {bal:,.2f}-SBI",
        f"SBI: Rs.{amt:,.2f} credited to A/c {acct} on {date}. From: {merchant}. {bal_str}",
        f"Dear SBI Customer, Rs.{amt:,.2f} credited to your A/c ending {acct} from {merchant}. {bal_str}",
    ]
    return _build_item(random.choice(templates), merchant, None, acct, "SBI", bal_str, is_credit=True)


def gen_icici_debit() -> dict:
    merchant = random_merchant()
    amt = random_amount()
    acct = random_acct_number()
    bal = random_amount(1000, 1000000)
    date = random_date()
    ref = random_ref_no()
    
    templates = [
        f"ICICI Bank Acct {acct} debited with Rs {amt:,.2f} on {date}; {merchant}. UPI Ref No {ref}. Call 18001200 if not you",
        f"Dear Customer, INR {amt:,.2f} debited from your ICICI Bank A/c {acct} towards {merchant} on {date}. Avl Bal: INR {bal:,.2f}",
        f"ICICI Bank: Rs.{amt:,.2f} debited from Acct {acct} on {date} for {merchant}. Avl Bal Rs.{bal:,.2f}. Ref {ref}",
        f"Rs {amt:,.2f} debited from ICICI Bank A/c {acct} for {merchant} on {date}. Avl Bal: Rs {bal:,.2f}",
    ]
    text = random.choice(templates)
    return _build_item(text, merchant, None, acct, "ICICI", None)


def gen_icici_credit() -> dict:
    merchant = random_merchant()
    amt = random_amount()
    acct = random_acct_number()
    bal = random_amount(1000, 1000000)
    date = random_date()
    
    templates = [
        f"ICICI Bank Acct {acct} credited with Rs {amt:,.2f} on {date} from {merchant}. Avl Bal Rs {bal:,.2f}",
        f"Dear Customer, your ICICI Bank A/c {acct} has been credited with INR {amt:,.2f} by {merchant} on {date}. Avl Bal INR {bal:,.2f}",
    ]
    return _build_item(random.choice(templates), merchant, None, acct, "ICICI", None, is_credit=True)


def gen_axis_debit() -> dict:
    merchant = random_merchant()
    amt = random_amount()
    acct = random_acct_number()
    bal = random_amount(1000, 1000000)
    date = random_date()
    
    templates = [
        f"Rs.{amt:,.2f} spent on your Axis Bank A/c {acct} at {merchant} on {date}. Avl Bal: Rs.{bal:,.2f}",
        f"Axis Bank: INR {amt:,.2f} debited from A/c no. {acct} on {date} towards {merchant}. Avl Lmt: INR {bal:,.2f}. Not you? Call 18604195555",
        f"Alert from Axis Bank! Rs {amt:,.2f} debited from your account {acct} for {merchant} on {date}. Bal Rs {bal:,.2f}",
    ]
    return _build_item(random.choice(templates), merchant, None, acct, "Axis", None)


def gen_axis_credit() -> dict:
    merchant = random_merchant()
    amt = random_amount()
    acct = random_acct_number()
    bal = random_amount(1000, 1000000)
    date = random_date()
    
    templates = [
        f"Axis Bank: INR {amt:,.2f} credited to your A/c {acct} on {date} from {merchant}. Avl Bal INR {bal:,.2f}",
        f"Rs.{amt:,.2f} credited to your Axis Bank A/c {acct} by {merchant} on {date}. Avl Bal: Rs.{bal:,.2f}",
    ]
    return _build_item(random.choice(templates), merchant, None, acct, "Axis", None, is_credit=True)


def gen_kotak_debit() -> dict:
    merchant = random_merchant()
    amt = random_amount()
    acct = random_acct_number()
    bal = random_amount(1000, 1000000)
    date = random_date()
    
    templates = [
        f"Kotak Bank: Rs.{amt:,.2f} debited from your A/c {acct} to {merchant} on {date}. Avl Bal Rs.{bal:,.2f}",
        f"INR {amt:,.2f} spent using Kotak Debit Card {acct} at {merchant} on {date}. Avl Bal: INR {bal:,.2f}. Not you? Call 18602662666",
        f"Alert! Rs {amt:,.2f} debited from Kotak A/c {acct} for {merchant}. Date: {date}. Avl Bal Rs {bal:,.2f}",
    ]
    return _build_item(random.choice(templates), merchant, None, acct, "Kotak", None)


def gen_bob_credit() -> dict:
    """Bank of Baroda style credit SMS."""
    merchant = random_merchant()
    amt = random_amount()
    acct = random_acct_number()
    bal = random_amount(1000, 1000000)
    date = random_date()
    ref = random_ref_no()
    
    templates = [
        f"Dear BOB UPI User, your account is credited INR {amt:,.2f} on Date {date} by UPI Ref No {ref} - Bank of Baroda",
        f"Rs.{int(amt)} Credited to A/c {acct} thru UPI/{ref} by {merchant}. Total Bal:Rs.{bal:,.2f}CR. Avlbl Amt:Rs.{bal:,.2f}({date}) - Bank of Baroda",
        f"BOB: INR {amt:,.2f} credited to your A/c {acct} from {merchant} on {date}. Avl Bal: INR {bal:,.2f}",
    ]
    return _build_item(random.choice(templates), merchant, None, acct, "BOB", None, is_credit=True)


def gen_union_bank() -> dict:
    """Union Bank of India style SMS."""
    merchant = random_merchant()
    amt = random_amount()
    acct = random_acct_number()
    bal = random_amount(1000, 1000000)
    date = random_date()
    tx_type = random.choice(["Transfer", "NEFT", "IMPS", "UPI"])
    credited = random.random() < 0.5
    
    templates = [
        f"Your SB A/c {acct} is {'Credited' if credited else 'Debited'} for Rs.{amt:,.2f} on {date} by {tx_type}. Avl Bal Rs:{bal:,.2f} -Union Bank of India",
        f"UBI: A/c {acct} {'credited' if credited else 'debited'} Rs.{amt:,.2f} on {date}. {tx_type} {'from' if credited else 'to'} {merchant}. Bal Rs.{bal:,.2f}",
    ]
    return _build_item(random.choice(templates), tx_type if random.random() < 0.3 else merchant, None, acct, "Union Bank of India", None, is_credit=credited)


def gen_upi_generic() -> dict:
    """Generic UPI transaction SMS."""
    bank_full, bank_short = random_bank()
    merchant = random_merchant()
    amt = random_amount(1, 100000)
    acct = random_acct_number()
    bal = random_amount(100, 500000)
    date = random_date()
    ref = random_ref_no()
    debited = random.random() < 0.5
    
    templates = [
        f"Rs.{amt:,.2f} {'debited' if debited else 'credited'} {'from' if debited else 'to'} A/c {acct} {'to' if debited else 'from'} {merchant} via UPI. Ref: {ref}. Bal: Rs.{bal:,.2f}-{bank_short}",
        f"UPI txn: Rs {amt:,.2f} {'sent to' if debited else 'received from'} {merchant} from A/c {acct}. UPI Ref {ref}. {bank_short}",
        f"{bank_short}: Rs.{amt:,.2f} {'paid to' if debited else 'received from'} {merchant} on {date} via UPI. A/c {acct}. Bal Rs.{bal:,.2f}",
    ]
    return _build_item(random.choice(templates), merchant, None, acct, bank_short, None, is_credit=not debited)


def gen_neft_imps() -> dict:
    """NEFT/IMPS transaction SMS."""
    bank_full, bank_short = random_bank()
    merchant = random_merchant()
    amt = random_amount(100, 500000)
    acct = random_acct_number()
    bal = random_amount(1000, 1000000)
    date = random_date()
    ref = random_ref_no()
    mode = random.choice(["NEFT", "IMPS", "RTGS"])
    credited = random.random() < 0.5
    
    templates = [
        f"{bank_short}: INR {amt:,.2f} {'credited to' if credited else 'debited from'} A/c {acct} via {mode} on {date}. {'From' if credited else 'To'}: {merchant}. Ref: {ref}. Avl Bal INR {bal:,.2f}",
        f"Rs.{amt:,.2f} {'received' if credited else 'transferred'} {'from' if credited else 'to'} {merchant} via {mode}. A/c {acct}. Bal Rs.{bal:,.2f}. {bank_short}",
        f"Dear Customer, {mode} of Rs {amt:,.2f} {'credited to' if credited else 'debited from'} your {bank_short} A/c {acct} on {date}. Ref {ref}. Avl Bal Rs {bal:,.2f}",
    ]
    return _build_item(random.choice(templates), merchant, None, acct, bank_short, None, is_credit=credited)


def gen_atm_withdrawal() -> dict:
    """ATM withdrawal SMS."""
    bank_full, bank_short = random_bank()
    amt = random_amount(100, 25000)
    # Round to nearest 100 for ATM
    amt = round(amt / 100) * 100
    acct = random_acct_number()
    bal = random_amount(100, 500000)
    date = random_date()
    
    atm_labels = ["ATM", "ATM Cash", "Cash Withdrawal", "Self ATM"]
    atm_merchant = random.choice(atm_labels)
    
    templates = [
        f"{bank_short}: Rs.{amt:,.2f} withdrawn from ATM using A/c {acct} on {date}. Avl Bal: Rs.{bal:,.2f}",
        f"Dear Customer, INR {amt:,.2f} debited from your {bank_short} A/c {acct} for ATM Cash on {date}. Avl Bal INR {bal:,.2f}",
        f"ATM Withdrawal of Rs {amt:,} from A/c {acct} on {date}. Avl Bal Rs {bal:,.2f}. {bank_short}",
        f"Rs.{amt:,} debited from {bank_short} A/c {acct} for {atm_merchant} on {date}. Bal Rs.{bal:,.2f}",
    ]
    return _build_item(random.choice(templates), atm_merchant, None, acct, bank_short, None)


def gen_pos_transaction() -> dict:
    """POS (Point of Sale) card swipe transaction."""
    bank_full, bank_short = random_bank()
    merchant = random.choice(ALL_MERCHANTS)
    amt = random_amount(50, 50000)
    acct = random_acct_number()
    bal = random_amount(100, 500000)
    date = random_date()
    card_type = random.choice(["Debit Card", "Credit Card"])
    
    templates = [
        f"{bank_short}: Rs.{amt:,.2f} spent on your {card_type} {acct} at {merchant} on {date}. Avl Bal: Rs.{bal:,.2f}",
        f"INR {amt:,.2f} debited from {bank_short} {card_type} ending {acct} at POS-{merchant} on {date}. Avl Lmt Rs.{bal:,.2f}",
        f"Alert! Rs {amt:,.2f} spent at {merchant} using {bank_short} Card {acct} on {date}. Bal Rs {bal:,.2f}. Not you? Call 18001234",
        f"Transaction Alert: Rs.{amt:,.2f} POS purchase at {merchant} on {date}. Card {acct}. {bank_short}. Avl Bal Rs.{bal:,.2f}",
    ]
    return _build_item(random.choice(templates), merchant, None, acct, bank_short, None)


def gen_emi_debit() -> dict:
    """EMI/Loan deduction SMS."""
    bank_full, bank_short = random_bank()
    amt = random_amount(1000, 50000)
    acct = random_acct_number()
    bal = random_amount(100, 500000)
    date = random_date()
    emi_for = random.choice([
        "Home Loan EMI", "Car Loan EMI", "Personal Loan EMI",
        "Education Loan EMI", "EMI payment", "Loan A/c repayment",
        "Credit Card EMI", "Bajaj Finserv EMI", "Consumer Durable Loan",
    ])
    
    templates = [
        f"{bank_short}: Rs.{amt:,.2f} debited from A/c {acct} towards {emi_for} on {date}. Avl Bal Rs.{bal:,.2f}",
        f"Dear Customer, INR {amt:,.2f} auto-debited from your {bank_short} A/c {acct} for {emi_for}. Date: {date}. Avl Bal INR {bal:,.2f}",
        f"EMI Debit: Rs {amt:,.2f} from A/c {acct} for {emi_for} on {date}. Bal Rs {bal:,.2f}. {bank_short}",
    ]
    return _build_item(random.choice(templates), emi_for, None, acct, bank_short, None)


def gen_salary_credit() -> dict:
    """Salary credit SMS."""
    bank_full, bank_short = random_bank()
    amt = random_amount(15000, 300000)
    acct = random_acct_number()
    bal = random_amount(amt, amt + 500000)
    date = random_date()
    company = random.choice([
        "SALARY", "salary credit", "Infosys Ltd", "TCS", "Wipro",
        "HCL Technologies", "Tech Mahindra", "Cognizant",
        "Accenture", "IBM India", "Amazon India", "Google India",
        "Microsoft India", "Flipkart", "PayTM",
        "NEFT from employer", "Monthly Salary",
    ])
    
    templates = [
        f"{bank_short}: INR {amt:,.2f} credited to your A/c {acct} on {date}. Info: {company}. Avl Bal INR {bal:,.2f}",
        f"Your A/c {acct} credited with Rs.{amt:,.2f} by {company} on {date}. Avl Bal Rs.{bal:,.2f}-{bank_short}",
        f"Dear Customer, Rs {amt:,.2f} credited to {bank_short} A/c {acct} from {company}. Date {date}. Bal Rs {bal:,.2f}",
    ]
    return _build_item(random.choice(templates), company, None, acct, bank_short, None, is_credit=True)


def gen_bill_payment() -> dict:
    """Bill payment / auto-debit SMS."""
    bank_full, bank_short = random_bank()
    amt = random_amount(50, 10000)
    acct = random_acct_number()
    bal = random_amount(100, 200000)
    date = random_date()
    biller = random.choice(MERCHANTS_UTILITY + [
        "Vodafone Postpaid", "Netflix", "Hotstar", "Amazon Prime",
        "Spotify", "YouTube Premium", "iCloud Storage",
        "Municipal Tax", "Property Tax", "Insurance Premium",
        "Broadband Bill", "Gas Bill", "LPG Refill",
    ])
    
    templates = [
        f"{bank_short}: Rs.{amt:,.2f} debited from A/c {acct} for BillPay/{biller} on {date}. Avl Bal Rs.{bal:,.2f}",
        f"Bill Payment: INR {amt:,.2f} paid to {biller} from {bank_short} A/c {acct}. Date {date}. Bal INR {bal:,.2f}",
        f"Auto-debit of Rs {amt:,.2f} from A/c {acct} towards {biller} on {date}. Avl Bal Rs {bal:,.2f}. {bank_short}",
        f"Dear Customer, Rs.{amt:,.2f} debited from your {bank_short} A/c {acct} for {biller}. Bal Rs.{bal:,.2f}",
    ]
    return _build_item(random.choice(templates), biller, None, acct, bank_short, None)


def gen_credit_card_txn() -> dict:
    """Credit card transaction SMS."""
    bank_full, bank_short = random_bank()
    merchant = random.choice(ALL_MERCHANTS)
    amt = random_amount(50, 100000)
    card_last4 = ''.join(random.choices(string.digits, k=4))
    date = random_date()
    limit = random_amount(amt, 500000)
    
    templates = [
        f"{bank_short} Credit Card {card_last4}: Rs.{amt:,.2f} spent at {merchant} on {date}. Avl Lmt: Rs.{limit:,.2f}. Dispute? Call 18001234",
        f"Alert: Rs {amt:,.2f} charged to your {bank_short} Credit Card ending {card_last4} at {merchant} on {date}. Avl Lmt Rs {limit:,.2f}",
        f"Transaction of Rs.{amt:,.2f} on {bank_short} CC XX{card_last4} at {merchant}. Date: {date}. Avl Lmt: Rs.{limit:,.2f}",
        f"{bank_short}: INR {amt:,.2f} spent on Credit Card {card_last4} at {merchant} on {date}. Avlbl Limit INR {limit:,.2f}",
    ]
    return _build_item(random.choice(templates), merchant, None, card_last4, bank_short, None)


def gen_pnb_canara() -> dict:
    """PNB / Canara Bank style SMS."""
    bank = random.choice(["PNB", "Canara"])
    bank_full = "Punjab National Bank" if bank == "PNB" else "Canara Bank"
    merchant = random_merchant()
    amt = random_amount()
    acct = random_acct_number()
    bal = random_amount(100, 500000)
    date = random_date()
    credited = random.random() < 0.5
    
    templates = [
        f"{bank}: A/c {acct} {'credited' if credited else 'debited'} Rs.{amt:,.2f} on {date} {'from' if credited else 'for'} {merchant}. Avl Bal Rs.{bal:,.2f}",
        f"Dear {bank} Customer, Rs {amt:,.2f} {'credited to' if credited else 'debited from'} your A/c {acct} on {date}. Ref: {merchant}. Bal Rs {bal:,.2f}",
        f"{bank_full}: Rs.{amt:,.2f} {'received from' if credited else 'paid to'} {merchant}. A/c {acct}. Date {date}. Avl Bal Rs.{bal:,.2f}",
    ]
    return _build_item(random.choice(templates), merchant, None, acct, bank, None, is_credit=credited)


def gen_indusind_yes_federal() -> dict:
    """IndusInd / Yes / Federal Bank style SMS."""
    banks = [("IndusInd", "IndusInd Bank"), ("YesBk", "Yes Bank"), ("FedBk", "Federal Bank")]
    bank_short, bank_full = random.choice(banks)
    merchant = random_merchant()
    amt = random_amount()
    acct = random_acct_number()
    bal = random_amount(100, 500000)
    date = random_date()
    credited = random.random() < 0.5
    
    templates = [
        f"{bank_full}: Rs.{amt:,.2f} {'credited to' if credited else 'debited from'} your A/c {acct} on {date}. {'From' if credited else 'To'}: {merchant}. Avl Bal Rs.{bal:,.2f}",
        f"Dear Customer, INR {amt:,.2f} has been {'credited to' if credited else 'debited from'} {bank_short} A/c {acct} for {merchant} on {date}. Bal INR {bal:,.2f}",
    ]
    return _build_item(random.choice(templates), merchant, None, acct, bank_short, None, is_credit=credited)


def gen_wallet_txn() -> dict:
    """Digital wallet / fintech transaction."""
    wallet = random.choice(["Paytm", "PhonePe", "Google Pay", "Amazon Pay", "MobiKwik"])
    merchant = random.choice(ALL_MERCHANTS)
    amt = random_amount(1, 10000)
    date = random_date()
    ref = random_ref_no()
    
    templates = [
        f"{wallet}: Rs.{amt:,.2f} paid to {merchant} on {date}. Txn ID: {ref}",
        f"Payment of Rs {amt:,.2f} to {merchant} via {wallet} successful. Ref: {ref}. Date: {date}",
        f"Rs.{amt:,.2f} debited from {wallet} wallet for {merchant} on {date}. Ref {ref}",
    ]
    text = random.choice(templates)
    tokens = tokenize(text)
    
    # Find entities
    merchant_tokens = tokenize(merchant)
    merchant_idx = find_token_indices(tokens, merchant_tokens)
    
    amt_str = f"Rs.{amt:,.2f}"
    amt_tokens = tokenize(amt_str)
    amt_idx = find_token_indices(tokens, amt_tokens)
    if not amt_idx:
        amt_str2 = f"Rs {amt:,.2f}"
        amt_tokens2 = tokenize(amt_str2)
        amt_idx = find_token_indices(tokens, amt_tokens2)
    
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
        if t.lower() in ["a/c", "a/c", "ac", "acct"]:
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
    """Find amount-related tokens (first occurrence of Rs/INR + number)."""
    for i, t in enumerate(tokens):
        # Pattern: "Rs.1500" (combined)
        if re.match(r'^Rs\.[\d,]+\.?\d*$', t, re.IGNORECASE):
            return [i]
        # Pattern: "INR" or "Rs" followed by number
        if t.upper() in ["INR", "RS", "RS."]:
            if i + 1 < len(tokens) and re.match(r'^[\d,]+\.?\d*$', tokens[i+1]):
                return [i, i+1]
    return []


def _find_balance_tokens(tokens: List[str]) -> List[int]:
    """Find balance-related tokens."""
    for i, t in enumerate(tokens):
        lower = t.lower()
        # Look for "Avl Bal", "Bal:", "Balance", "Avlbl"
        if lower in ["avl", "avlbl", "available"]:
            # Collect balance context + amount
            idx = [i]
            j = i + 1
            while j < len(tokens) and j <= i + 4:
                tj = tokens[j].lower()
                if tj in ["bal", "bal:", "balance", "amt", "amt:", "lmt", "lmt:"]:
                    idx.append(j)
                elif tj in ["inr", "rs", "rs.", "rs:"] or re.match(r'^Rs[\.:]\d', tokens[j]):
                    idx.append(j)
                    # Check for following number
                    if j + 1 < len(tokens) and re.match(r'^[\d,]+\.?\d*$', tokens[j+1]):
                        idx.append(j + 1)
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
            if re.match(r'^Bal:Rs\.[\d,]+\.?\d*', t):
                return [i]
            if i + 1 < len(tokens):
                nxt = tokens[i+1]
                if nxt.upper() in ["INR", "RS", "RS."] or re.match(r'^Rs\.[\d,]', nxt):
                    idx = [i, i+1]
                    if i + 2 < len(tokens) and re.match(r'^[\d,]+\.?\d*', tokens[i+2]):
                        idx.append(i + 2)
                    return idx
        
        # "Total Bal:" pattern
        if lower == "total" and i + 1 < len(tokens) and tokens[i+1].lower().startswith("bal"):
            idx = [i, i+1]
            j = i + 2
            if j < len(tokens) and (re.match(r'^Rs\.[\d,]', tokens[j]) or tokens[j].upper() in ["INR", "RS"]):
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
    (gen_hdfc_debit, 8),
    (gen_hdfc_credit, 6),
    (gen_sbi_debit, 8),
    (gen_sbi_credit, 6),
    (gen_icici_debit, 7),
    (gen_icici_credit, 5),
    (gen_axis_debit, 6),
    (gen_axis_credit, 4),
    (gen_kotak_debit, 5),
    (gen_bob_credit, 5),
    (gen_union_bank, 5),
    (gen_upi_generic, 10),
    (gen_neft_imps, 7),
    (gen_atm_withdrawal, 5),
    (gen_pos_transaction, 7),
    (gen_emi_debit, 4),
    (gen_salary_credit, 5),
    (gen_bill_payment, 6),
    (gen_credit_card_txn, 6),
    (gen_pnb_canara, 4),
    (gen_indusind_yes_federal, 4),
    (gen_wallet_txn, 4),
]


def generate_synthetic_data(count: int, seed: int = 42) -> List[dict]:
    """Generate N synthetic NER training items."""
    random.seed(seed)
    
    # Build weighted generator list
    weighted_gens = []
    for gen_fn, weight in GENERATORS:
        weighted_gens.extend([gen_fn] * weight)
    
    items = []
    retries = 0
    max_retries = count * 3
    
    while len(items) < count and retries < max_retries:
        gen_fn = random.choice(weighted_gens)
        try:
            item = gen_fn()
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
    parser = argparse.ArgumentParser(description="Generate synthetic NER training data")
    parser.add_argument("--output", default="data/synthetic_ner_data.json",
                        help="Output JSON file path")
    parser.add_argument("--count", type=int, default=500,
                        help="Number of synthetic items to generate (default: 500)")
    parser.add_argument("--seed", type=int, default=42,
                        help="Random seed for reproducibility")
    args = parser.parse_args()
    
    print("=" * 60)
    print("Synthetic NER Training Data Generator")
    print("=" * 60)
    
    print(f"\n⚙️  Generating {args.count} synthetic items (seed={args.seed})...")
    items = generate_synthetic_data(args.count, args.seed)
    
    print_stats(items)
    
    # Save
    output_path = args.output
    if not output_path.startswith("/"):
        # Relative path - resolve from script directory
        import os
        script_dir = os.path.dirname(os.path.abspath(__file__))
        output_path = os.path.join(script_dir, output_path)
    
    import os
    os.makedirs(os.path.dirname(output_path), exist_ok=True)
    
    with open(output_path, "w", encoding="utf-8") as f:
        json.dump(items, f, indent=4, ensure_ascii=False)
    
    print(f"\n✅ Saved {len(items)} items to: {output_path}")
    print(f"   File size: {os.path.getsize(output_path) / 1024:.1f} KB")
    print(f"\nNext step: python prepare_data.py --input data/ --output output/dataset/ --augment-banks 3")


if __name__ == "__main__":
    main()
