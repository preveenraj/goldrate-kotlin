#!/usr/bin/env python3
"""Builds the rates feed the app reads instead of scraping on-device.

Scraping from CI rather than from the phone means a source changing its markup
is fixed by editing this file and pushing — every user picks it up on their next
launch, with no Play release. The app keeps its own scrapers as a fallback for
when this feed is stale or unreachable, so this is a fast path, not a
single point of failure.

Sources and their quirks are documented per-parser below. Run:

    python tools/build_rates.py --out public/rates.json
"""

import argparse
import json
import re
import sys
import time
import urllib.error
import urllib.request
from datetime import datetime, timedelta, timezone

SCHEMA = 1

UA = (
    "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) "
    "Chrome/120.0.0.0 Mobile Safari/537.36"
)
HEADERS = {"User-Agent": UA, "Accept": "text/html", "Connection": "close"}
TIMEOUT = 30
ATTEMPTS = 3

KERALAGOLD_GOLD = "https://www.keralagold.com/kerala-gold-rate-per-gram.htm"
GOODRETURNS_GOLD = "https://www.goodreturns.in/gold-rates/kerala.html"
GOODRETURNS_SILVER = "https://www.goodreturns.in/silver-rates/kerala.html"
BANKBAZAAR_SILVER = "https://www.bankbazaar.com/silver-rate-kerala.html"

RUPEE = re.compile(r"₹\s*([\d,]+)")
TAGS = re.compile(r"<[^>]+>")


def log(msg):
    print(msg, file=sys.stderr)


def fetch(url):
    """GETs a page, retrying transient failures. Returns None if all attempts fail."""
    for attempt in range(1, ATTEMPTS + 1):
        try:
            req = urllib.request.Request(url, headers=HEADERS)
            with urllib.request.urlopen(req, timeout=TIMEOUT) as resp:
                return resp.read().decode("utf-8", "replace")
        except Exception as exc:  # noqa: BLE001 - any failure is just a retry
            log(f"  attempt {attempt}/{ATTEMPTS} failed for {url}: {exc}")
            if attempt < ATTEMPTS:
                time.sleep(2 * attempt)
    return None


def decode_entities(html):
    for entity, char in (
        ("&#x20b9;", "₹"), ("&#8377;", "₹"), ("&rupee;", "₹"),
        ("&nbsp;", " "), ("&amp;", "&"),
    ):
        html = html.replace(entity, char)
    return html


def strip_tags(fragment):
    return " ".join(TAGS.sub(" ", fragment).split())


def row_text(row):
    """Visible text of a chunk produced by splitting on '<tr'.

    The split leaves that tag's own attributes at the head with no '<' to strip
    them by, so they are cut at the first '>' before the remaining tags come
    out — otherwise class names land in the parsed text.
    """
    body = row.split(">", 1)[1] if ">" in row else row
    return strip_tags(body)


# --- gold: keralagold.com (primary) ---------------------------------------

KG_DATE = re.compile(r"\d{1,2}-[A-Za-z]{3}-\d{2}")
KG_RATE = re.compile(r"\d{1,3}(?:,\d{3})+|\d{4,}")
KG_SESSION = re.compile(r"\((Morning|Afternoon|Evening|Night)\)")
CELLSPACING_0 = re.compile(r'cellspacing\s*=\s*"?0(?!\d)', re.I)
WIDTH_280 = re.compile(r'width\s*=\s*"?280(?!\d)', re.I)


def keralagold_table(html):
    """The 280px rate table. Attributes are matched individually so the site can
    reorder them without breaking this."""
    for match in re.finditer(r"<table[^>]*>", html, re.I):
        tag = match.group(0)
        if not CELLSPACING_0.search(tag) or not WIDTH_280.search(tag):
            continue
        end = html.find("</table>", match.end())
        if end < 0:
            continue
        return html[match.end():end]
    return None


def parse_keralagold(html):
    table = keralagold_table(html)
    if table is None:
        return None
    points = []
    for row in table.split("<tr"):
        date = KG_DATE.search(row)
        if not date:
            continue
        rate = KG_RATE.search(row[date.end():])
        if not rate:
            continue
        session = KG_SESSION.search(row)
        points.append({
            "date": date.group(0),
            "rate": int(rate.group(0).replace(",", "")),
            "session": session.group(1) if session else None,
        })
    if not points:
        return None
    latest, previous = points[-1], (points[-2] if len(points) > 1 else None)
    return {
        "source": "keralagold.com",
        "periodLabel": "Month",
        "rate": latest["rate"],
        "date": latest["date"],
        "session": latest["session"],
        "change": latest["rate"] - previous["rate"] if previous else 0,
        "history": points,
    }


# --- gold: goodreturns.in (backup) ----------------------------------------

GR_DATE = re.compile(r"([A-Z][a-z]{2})[a-z]*\s+(\d{1,2}),\s*(\d{4})")


def normalise_long_date(match):
    mon, day, year = match.group(1), match.group(2), match.group(3)
    return f"{int(day)}-{mon}-{year[-2:]}"


def rupee_column_for(header_row, label):
    """Index among the amount-bearing columns for `label`, read off the header
    so an added 18K or per-8g column can't shift which number we publish."""
    cells = [strip_tags(c) for c in re.split(r"<t[dh][\s>]", header_row, flags=re.I)[1:]]
    for index, cell in enumerate(cells):
        if cell.strip().lower() == label.lower():
            return index - 1 if index > 0 else None
    return None


def parse_goodreturns_gold(html):
    html = decode_entities(html)
    start = html.find("Last 10 Days")
    if start < 0:
        return None
    end = html.find("</table>", start)
    end = len(html) if end < 0 else end
    column = None
    points = []
    for row in html[start:end].split("<tr"):
        if column is None:
            column = rupee_column_for(row, "22K")
        text = strip_tags(row)
        date = GR_DATE.search(text)
        if not date:
            continue
        amounts = [int(a.replace(",", "")) for a in RUPEE.findall(text)]
        if not amounts:
            continue
        rate = amounts[column] if column is not None and column < len(amounts) else amounts[-1]
        points.append({"date": normalise_long_date(date), "rate": rate, "session": None})
    if not points:
        return None
    points.reverse()  # printed newest-first; the app charts left-to-right
    latest, previous = points[-1], (points[-2] if len(points) > 1 else None)
    return {
        "source": "goodreturns.in",
        "periodLabel": "10-Day",
        "rate": latest["rate"],
        "date": latest["date"],
        "session": None,
        "change": latest["rate"] - previous["rate"] if previous else 0,
        "history": points,
    }


# --- silver: goodreturns.in (primary) -------------------------------------

GROUPED = re.compile(r"\d{1,3}(?:,\d{2,3})+")
TREND_PCT = re.compile(r"\(([+-]?\d+(?:\.\d+)?)%\)")
MONTH_BLOCK = re.compile(
    r"Silver Price Movement in [A-Za-z ]+?,?\s*([A-Z][a-z]+ \d{4})\s*</span>.*?<tbody>(.*?)</tbody>",
    re.S,
)


def parse_goodreturns_silver(html):
    html = decode_entities(html)
    start = html.find("Last 10 Days")
    if start < 0:
        return None
    end = html.find("</table>", start)
    end = len(html) if end < 0 else end
    points = []
    for row in html[start:end].split("<tr"):
        text = strip_tags(row)
        date = GR_DATE.search(text)
        if not date:
            continue
        amounts = [int(a.replace(",", "")) for a in RUPEE.findall(text)]
        if not amounts:
            continue
        # Largest amount in the row is per-kg in both the desktop (10g/100g/1kg)
        # and mobile (kg only) layouts; the day's change sits outside the ₹.
        points.append({"date": normalise_long_date(date), "rate": max(amounts)})
    if not points:
        return None
    points.reverse()

    months = []
    for block in MONTH_BLOCK.finditer(html):
        text = strip_tags(block.group(2))
        values = [int(v.replace(",", "")) for v in GROUPED.findall(text)]
        if len(values) < 4:
            continue
        pct = TREND_PCT.search(text)
        months.append({
            "label": block.group(1),
            "open": values[0], "close": values[1],
            "high": values[2], "low": values[3],
            "trendPct": float(pct.group(1)) if pct else 0.0,
        })

    latest, previous = points[-1], (points[-2] if len(points) > 1 else None)
    return {
        "source": "goodreturns.in",
        "periodLabel": "10-Day",
        "perKg": latest["rate"],
        "date": latest["date"],
        "change": latest["rate"] - previous["rate"] if previous else 0,
        "history": points,
        "months": months,
    }


# --- silver: bankbazaar.com (backup) --------------------------------------

# The page writes the stamp "Updated On - 01 Sep 2026"; a bare "Updated on"
# label appears earlier with its date in a sibling element. Only the stamp is
# followed by a digit, so the first match is the right one. The month stays
# case-sensitive because it is used to build the date string verbatim.
BB_UPDATED = re.compile(
    r"Updated\s+[Oo]n\s*[-\u2013\u2014:]?\s*(\d{1,2})\s+([A-Z][a-z]{2})[a-z]*\s+(\d{4})"
)
BB_PER_KG = re.compile(r"\b1\s*kg\b", re.I)
BB_PER_GRAM = re.compile(r"\b1\s*gram\b", re.I)


def previous_day(date):
    try:
        parsed = datetime.strptime(date, "%d-%b-%y")
    except ValueError:
        return None
    stepped = parsed.fromordinal(parsed.toordinal() - 1)
    return f"{stepped.day}-{stepped.strftime('%b')}-{stepped.strftime('%y')}"


def parse_bankbazaar_silver(html):
    html = decode_entities(html)
    start = html.find("Today & Yesterday")
    if start < 0:
        return None
    end = html.find("</table>", start)
    end = len(html) if end < 0 else end
    per_kg = per_gram = None
    for row in html[start:end].split("<tr"):
        text = row_text(row)
        amounts = [int(a.replace(",", "")) for a in RUPEE.findall(text)]
        if len(amounts) < 2:
            continue
        # The row reads "1 kg ₹2,55,000 ₹2,60,000 ₹5,000 ▼": today, yesterday,
        # then the change — so only the first two amounts are rates.
        if BB_PER_KG.search(text) and per_kg is None:
            per_kg = amounts[:2]
        elif BB_PER_GRAM.search(text) and per_gram is None:
            per_gram = amounts[:2]
    rates = per_kg or ([g * 1000 for g in per_gram] if per_gram else None)
    if not rates:
        return None
    today, yesterday = rates

    stamp = BB_UPDATED.search(strip_tags(html))
    if stamp:
        date = f"{int(stamp.group(1))}-{stamp.group(2)}-{stamp.group(3)[-2:]}"
    else:
        now = datetime.now(timezone.utc)
        date = f"{now.day}-{now.strftime('%b')}-{now.strftime('%y')}"

    history = []
    prev_date = previous_day(date)
    if prev_date:
        history.append({"date": prev_date, "rate": yesterday})
    history.append({"date": date, "rate": today})
    return {
        "source": "bankbazaar.com",
        "periodLabel": "2-Day",
        "perKg": today,
        "date": date,
        "change": today - yesterday,
        "history": history,
        "months": [],  # disagrees with goodreturns; never mix the two histories
    }


# --- assembly -------------------------------------------------------------

# Kerala rates roll over on the Indian calendar day, not on UTC's. The workflow
# runs on UTC runners, so every "is this today's rate?" question is asked in IST.
IST = timezone(timedelta(hours=5, minutes=30))


def parse_rate_date(value):
    """Parses a scraped "5-Sep-26" (or "05-Sep-26") into a date, or None."""
    try:
        return datetime.strptime(value, "%d-%b-%y").date()
    except (TypeError, ValueError):
        return None


def today_ist():
    return datetime.now(IST).date()


def first_of(label, candidates):
    """Runs each (name, url, parser) in order and returns the first that yields
    *today's* data, so the feed itself carries the same fallback chain as the app.

    A source that answers with an older date is not an answer: the sources
    publish the day's rate at different times, and a morning run would otherwise
    take yesterday's figure from the first source and never ask the second one
    that already has today's. Such a reading is held aside and the next source
    is tried; the newest of them is published only if no source has today,
    which is the honest result on a Sunday or a holiday.
    """
    today = today_ist()
    best = None
    best_date = None
    for name, url, parser in candidates:
        log(f"{label}: trying {name}")
        html = fetch(url)
        if html is None:
            continue
        try:
            parsed = parser(html)
        except Exception as exc:  # noqa: BLE001 - a broken parser is a failed source
            log(f"  {name} parser raised: {exc}")
            continue
        if not parsed:
            log(f"  {name} returned nothing")
            continue
        log(f"  {name} ok: {parsed.get('rate') or parsed.get('perKg')} on {parsed['date']}")
        parsed_date = parse_rate_date(parsed["date"])
        if parsed_date == today:
            return parsed
        log(f"  {name} is dated {parsed['date']}, not today ({today}); trying the next source")
        # An unparseable date sorts below any real one but still beats nothing.
        if best is None or (parsed_date is not None and (best_date is None or parsed_date > best_date)):
            best, best_date = parsed, parsed_date
    if best is not None:
        log(f"{label}: no source has today; publishing {best['source']} dated {best['date']}")
    return best


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", required=True)
    args = ap.parse_args()

    gold = first_of("gold", [
        ("keralagold.com", KERALAGOLD_GOLD, parse_keralagold),
        ("goodreturns.in", GOODRETURNS_GOLD, parse_goodreturns_gold),
    ])
    silver = first_of("silver", [
        ("goodreturns.in", GOODRETURNS_SILVER, parse_goodreturns_silver),
        ("bankbazaar.com", BANKBAZAAR_SILVER, parse_bankbazaar_silver),
    ])

    # Publishing a feed with a missing metal would tell the app "here is the
    # answer" while hiding half of it, and the app trusts a fresh feed over its
    # own scrapers. Fail instead: the last good deploy stays up, and the app
    # ages it out and scrapes for itself.
    if gold is None or silver is None:
        log(f"FAILED: gold={'ok' if gold else 'MISSING'} silver={'ok' if silver else 'MISSING'}")
        return 1

    now = datetime.now(timezone.utc)
    feed = {
        "schema": SCHEMA,
        "generatedAt": int(now.timestamp()),
        "generatedAtIso": now.strftime("%Y-%m-%dT%H:%M:%SZ"),
        "gold": gold,
        "silver": silver,
    }

    import os
    os.makedirs(os.path.dirname(args.out) or ".", exist_ok=True)
    with open(args.out, "w", encoding="utf-8") as handle:
        json.dump(feed, handle, indent=1, ensure_ascii=False)
        handle.write("\n")
    log(f"wrote {args.out}: gold {gold['rate']} on {gold['date']}, "
        f"silver {silver['perKg']} on {silver['date']}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
