import subprocess
import time
import re

def run_adb(command: str) -> str:
    """Runs an ADB shell command and returns the output."""
    try:
        result = subprocess.run(["adb", "shell"] + command.split(), capture_output=True, text=True, check=True)
        return result.stdout.strip()
    except subprocess.CalledProcessError as e:
        print(f"ADB Error executing {command}: {e.stderr.decode('utf-8') if type(e.stderr) is bytes else e.stderr}")
        return ""

def tap(x: int, y: int):
    """Taps at coordinates x, y"""
    run_adb(f"input tap {x} {y}")

def type_text(text: str):
    """Types text using adb input. Escapes spaces."""
    escaped = text.replace(" ", "%s")
    run_adb(f"input text '{escaped}'")

def swipe(x1: int, y1: int, x2: int, y2: int, duration_ms: int = 300):
    """Swipes from (x1, y1) to (x2, y2)"""
    run_adb(f"input swipe {x1} {y1} {x2} {y2} {duration_ms}")

def press_back():
    """Presses the hardware back button"""
    run_adb("input keyevent 4")

def press_home():
    """Presses the home button"""
    run_adb("input keyevent 3")

def clear_app_data(package_name="io.pm.finlight"):
    """Clears app data completely"""
    run_adb(f"pm clear {package_name}")

def launch_app(package_name="io.pm.finlight", activity=".MainActivity"):
    """Launches the app"""
    # Note: Using monkey is often easier than specifying exact activity, but am start is cleaner.
    run_adb(f"am start -n {package_name}/{package_name}{activity}")
    time.sleep(2) # Wait for launch

import random

def get_screen_size():
    """Gets the screen size from adb"""
    output = run_adb("wm size")
    # Output is usually like "Physical size: 1080x2400"
    match = re.search(r"(\d+)x(\d+)", output)
    if match:
        return int(match.group(1)), int(match.group(2))
    return 1080, 2400 # fallback

def random_monkey_events(count=5):
    """Executes random tap and swipe events to create chaos."""
    width, height = get_screen_size()
    print(f"Executing {count} random monkey events for chaos start...")
    for _ in range(count):
        event_type = random.choice(["tap", "swipe"])
        if event_type == "tap":
            x = random.randint(0, width)
            y = random.randint(0, height)
            tap(x, y)
        else:
            x1 = random.randint(0, width)
            y1 = random.randint(0, height)
            x2 = random.randint(0, width)
            y2 = random.randint(0, height)
            swipe(x1, y1, x2, y2, duration_ms=random.randint(100, 500))
        time.sleep(0.5)

def long_press(x: int, y: int, duration_ms: int = 1000):
    """Long presses at coordinates x, y"""
    run_adb(f"input swipe {x} {y} {x} {y} {duration_ms}")

def recent_apps():
    """Presses the recent apps button to background the app"""
    run_adb("input keyevent 187")

def clear_text(length: int = 50):
    """Clears text in the focused field by sending multiple backspace events."""
    for _ in range(length):
        run_adb("input keyevent 67")

def background_app(seconds: int = 3, package_name="io.pm.finlight", activity=".MainActivity"):
    """Backgrounds the app, waits, and brings it back to foreground."""
    print(f"Backgrounding app for {seconds} seconds...")
    press_home()
    time.sleep(seconds)
    launch_app(package_name, activity)

def trigger_process_death(package_name="io.pm.finlight", activity=".MainActivity"):
    """Simulates the Android OS killing the app for memory while it's in the background."""
    print("Triggering process death...")
    press_home()
    time.sleep(1)
    run_adb(f"am kill {package_name}")
    time.sleep(2)
    launch_app(package_name, activity)

def toggle_dark_mode():
    """Toggles the system dark mode state."""
    print("Toggling dark mode...")
    current_mode = run_adb("cmd uimode night")
    if "yes" in current_mode.lower():
        run_adb("cmd uimode night no")
    else:
        run_adb("cmd uimode night yes")


