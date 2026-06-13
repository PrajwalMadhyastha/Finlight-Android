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
2. The APP MAP (a JSON object containing previously visited screens, tested features, and known bugs).
3. The CURRENT UI STATE (a JSON array of interactable elements on the screen, with their center coordinates).
4. The ACTION HISTORY (what you have done so far).

Your secondary goal is to verify bugs. If the APP MAP contains bugs with Status "Open", you must attempt to reproduce them if you find yourself on the relevant screen. If you attempt to reproduce an "Open" bug and the behavior is now correct, explicitly state in your finish summary that the bug appears to be resolved.

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

def get_next_action(client, goal: str, ui_state: str, history: list, app_map_json: str = "") -> dict:
    prompt = f"HIGH-LEVEL GOAL:\n{goal}\n\n"
    if app_map_json:
        prompt += f"APP MAP (Avoid re-testing known features, try to reproduce Open bugs):\n{app_map_json}\n\n"
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

def summarize_run_to_app_map(client, current_app_map: dict, goal: str, run_summary: str, history: list) -> dict:
    prompt = f"""
We just completed a QA agent run. We need to update our persistent App Map.

Current App Map:
{json.dumps(current_app_map, indent=2)}

Run Goal:
{goal}

Run Summary (Contains found bugs and verified bugs):
{run_summary}

History of actions taken:
{json.dumps(history, indent=2)}

Based on the run summary and history, generate a JSON diff to update the App Map.
Only include NEW screens visited, NEW features tested, NEW bugs found, and any known bugs that should be marked as "Resolved".

Output schema:
{{
  "New_Screens": ["Screen Name"],
  "New_Features": ["Feature Name"],
  "New_Bugs": [
    {{"Description": "Bug description", "Status": "Open"}}
  ],
  "Resolved_Bugs": ["Description of the previously Open bug that is now resolved"]
}}

Return ONLY valid JSON. If there are no new items for a category, return an empty list.
"""
    try:
        response = client.models.generate_content(
            model='gemini-3.1-flash-lite',
            contents=prompt,
            config=types.GenerateContentConfig(temperature=0.0)
        )
        text_response = response.text.strip()
        if text_response.startswith("```json"):
            text_response = text_response[7:]
        elif text_response.startswith("```"):
            text_response = text_response[3:]
        if text_response.endswith("```"):
            text_response = text_response[:-3]
        
        return json.loads(text_response.strip())
    except Exception as e:
        print(f"Failed to generate or parse App Map update: {e}")
        return {"New_Screens": [], "New_Features": [], "New_Bugs": [], "Resolved_Bugs": []}
