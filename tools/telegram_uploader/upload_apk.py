import argparse
import asyncio
import hashlib
import os
import re
import time
from pathlib import Path

from telethon import TelegramClient
from telethon.sessions import StringSession


UPLOAD_TIMEOUT_SECONDS = 10 * 60


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def normalize_target(raw_target: str):
    """Accept normal Telegram usernames/links and internal numeric chat IDs."""
    target = raw_target.strip()

    # A private channel/group message link looks like:
    # https://t.me/c/3801981603/2
    # The internal ID must be represented to Telethon as -1003801981603.
    match = re.fullmatch(r"(?:https?://)?t\.me/c/(\d+)(?:/\d+)?/?", target)
    if match:
        return int(f"-100{match.group(1)}")

    # Telethon expects numeric peer IDs as integers, not strings.
    # This also fixes the common -100... format used for private channels/groups.
    if re.fullmatch(r"-100\d+", target):
        return int(target)

    if re.fullmatch(r"\d+", target):
        # Allow the convenient value copied from the /c/<id>/<message> URL.
        if len(target) >= 9:
            return int(f"-100{target}")
        return int(target)

    return target


async def main() -> None:
    parser = argparse.ArgumentParser(description="Upload a Screen-TL APK to Telegram.")
    parser.add_argument("apk", type=Path)
    parser.add_argument("--branch", default=os.environ.get("GITHUB_REF_NAME", "unknown"))
    parser.add_argument("--commit", default=os.environ.get("GITHUB_SHA", "unknown"))
    parser.add_argument("--run", default=os.environ.get("GITHUB_RUN_NUMBER", "unknown"))
    args = parser.parse_args()

    api_id = os.environ.get("TELEGRAM_API_ID")
    api_hash = os.environ.get("TELEGRAM_API_HASH")
    session = os.environ.get("TELEGRAM_SESSION")
    target_raw = os.environ.get("TELEGRAM_CHAT_ID")

    if not api_id or not api_hash or not session or not target_raw:
        raise SystemExit(
            "Missing Telegram configuration. Required secrets: "
            "TELEGRAM_API_ID, TELEGRAM_API_HASH, TELEGRAM_SESSION, TELEGRAM_CHAT_ID"
        )

    if not args.apk.is_file() or args.apk.stat().st_size == 0:
        raise SystemExit(f"APK does not exist or is empty: {args.apk}")

    target = normalize_target(target_raw)
    print("Telegram destination normalized successfully.", flush=True)

    digest = sha256(args.apk)
    size_mb = args.apk.stat().st_size / (1024 * 1024)
    caption = (
        "Screen-TL Build\n"
        f"Branch: {args.branch}\n"
        f"Build: #{args.run}\n"
        f"Commit: {args.commit[:12]}\n"
        f"Size: {size_mb:.1f} MiB\n"
        f"SHA-256: {digest}"
    )

    client = TelegramClient(StringSession(session), int(api_id), api_hash)
    await client.start()
    try:
        # Resolve the peer before uploading so access/entity problems are reported
        # separately from the actual file upload.
        await client.get_input_entity(target)
        print("Telegram destination resolved successfully.", flush=True)
        print(
            f"Uploading {args.apk} ({size_mb:.1f} MiB) to Telegram "
            f"(timeout: {UPLOAD_TIMEOUT_SECONDS // 60} minutes)...",
            flush=True,
        )

        last_reported_percent = -1
        started_at = time.monotonic()

        def progress_callback(current: int, total: int) -> None:
            nonlocal last_reported_percent
            if total <= 0:
                return
            percent = int(current * 100 / total)
            bucket = percent // 5 * 5
            if bucket != last_reported_percent:
                last_reported_percent = bucket
                elapsed = time.monotonic() - started_at
                print(
                    f"Telegram upload progress: {percent}% "
                    f"({current / (1024 * 1024):.1f}/{total / (1024 * 1024):.1f} MiB, "
                    f"{elapsed:.0f}s elapsed)",
                    flush=True,
                )

        await asyncio.wait_for(
            client.send_file(
                target,
                str(args.apk),
                caption=caption,
                force_document=True,
                supports_streaming=False,
                progress_callback=progress_callback,
            ),
            timeout=UPLOAD_TIMEOUT_SECONDS,
        )
        print("Telegram upload completed.", flush=True)
    except asyncio.TimeoutError:
        raise SystemExit(
            f"Telegram upload timed out after {UPLOAD_TIMEOUT_SECONDS // 60} minutes."
        )
    finally:
        await client.disconnect()


if __name__ == "__main__":
    asyncio.run(main())
