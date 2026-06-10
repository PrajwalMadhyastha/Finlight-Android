import os
import json
import time
from google import genai
from google.genai import types
from google.genai.errors import APIError

SYSTEM_PROMPT = """You are an autonomous exploratory Android QA tester.
Your goal is to explore the app, test features, and find bugs based on a given HIGH-LEVEL GOAL.

You will be provided with:
1. The HIGH-LEVEL GOAL.
2. The CURRENT UI STATE (a JSON array of interactable elements on the screen, with their center coordinates).
3. The ACTION HISTORY (what you have done so far).

You must think step-by-step (ReAct strategy) and output your next action in strict JSON format.

Supported actions:
- "tap": Taps a specific coordinate. Requires "x" and "y" in args.
- "long_press": Taps and holds a specific coordinate. Requires "x" and "y" in args.
- "type": Types text. Requires "text" in args. (Note: you must tap a text field before typing).
- "clear_text": Clears text in the currently focused text field. No args.
- "swipe": Swipes on the screen. Requires "x1", "y1", "x2", "y2" in args.
- "back": Presses the hardware back button. No args.
- "home": Presses the hardware home button. No args.
- "recent_apps": Presses the recent apps button to background the app. No args.
- "sleep": Waits for a specified number of seconds without doing anything. Requires "seconds" in args.
- "checkpoint": Wipes the detailed action history up to this point and replaces it with a summary. Use this when you complete a major stage (like onboarding). Requires "summary" in args.
- "finish": Ends the test. Requires "status" ("pass" or "fail") and "summary" (detailed markdown string of what was tested and bugs found) in args.

Always output valid JSON only, matching this schema:
{
  "reasoning": "Explanation of what you see and why you are choosing this action.",
  "action": "<action_name>",
  "args": {
     // arguments for the action
  }
}
"""

def setup_gemini():
    api_key = os.environ.get("GEMINI_API_KEY")
    if not api_key:
        print("WARNING: GEMINI_API_KEY environment variable not set.")
    # Initialize the new genai client
    client = genai.Client(api_key=api_key)
    return client

def get_next_action(client, goal: str, ui_state: str, history: list, memory_text: str = "") -> dict:
    prompt = f"HIGH-LEVEL GOAL:\n{goal}\n\n"
    if memory_text:
        prompt += f"[AVOID TESTING THESE PREVIOUSLY TESTED FEATURES]\n{memory_text}\n\n"
    prompt += f"ACTION HISTORY:\n{json.dumps(history, indent=2)}\n\n"
    prompt += f"CURRENT UI STATE:\n{ui_state}\n\n"
    prompt += "What is your next action? Respond in JSON format only."
    
    response = None
    for attempt in range(5):
        try:
            response = client.models.generate_content(
                model='gemini-3.1-flash-lite',
                contents=prompt,
                config=types.GenerateContentConfig(
                    system_instruction=SYSTEM_PROMPT,
                    temperature=0.9
                )
            )
            break
        except APIError as e:
            if e.code == 429:
                print(f"Rate limit exceeded (15 RPM). Waiting 60 seconds before retrying (Attempt {attempt+1}/5)...")
                time.sleep(60)
            else:
                print(f"API Error encountered: {e}")
                time.sleep(10)
        except Exception as e:
            if "429" in str(e) or "ResourceExhausted" in str(e):
                print(f"Rate limit exceeded (15 RPM). Waiting 60 seconds before retrying (Attempt {attempt+1}/5)...")
                time.sleep(60)
            else:
                print(f"Unexpected error: {e}")
                time.sleep(10)
            
    if not response:
        return {"action": "error", "reasoning": "Rate limit exhausted repeatedly", "args": {}}
        
    text_response = response.text
    
    # Clean up markdown code blocks if present
    text_response = text_response.strip()
    if text_response.startswith("```json"):
        text_response = text_response[7:]
    elif text_response.startswith("```"):
        text_response = text_response[3:]
    if text_response.endswith("```"):
        text_response = text_response[:-3]
        
    try:
        return json.loads(text_response.strip())
    except json.JSONDecodeError as e:
        print(f"Failed to parse LLM response as JSON: {text_response}")
        return {"action": "error", "reasoning": "JSON parse error", "args": {"raw_response": text_response}}
