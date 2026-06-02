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
