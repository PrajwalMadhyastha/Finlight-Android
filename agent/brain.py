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

Your secondary goal is to verify bugs and explore unknown areas.
- **Novelty:** Prioritize navigating to screens that you haven't visited yet, or screens that have few tested features. Avoid getting stuck testing the same screen repeatedly unless it's necessary for your goal.
- **Verification:** If the APP MAP contains bugs with Status "Open", attempt to reproduce them if you find yourself on the relevant screen. If you attempt to reproduce an "Open" bug and the behavior is now correct, state in your finish summary that it appears resolved. If the bug is still present, simply acknowledge it in your reasoning and MOVE ON. Do NOT fail the run because an already known "Open" bug is still present. Only fail the run if you discover a completely NEW critical bug or crash that blocks your goal.
- **Lifecycle Testing:** Occasionally use the `trigger_process_death` and `background_app` tools when in the middle of data entry (like composing a transaction) to verify that state is correctly saved and restored.

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
- "background_app": Backgrounds the app, waits, and restores it. Requires "seconds" (int) in args.
- "trigger_process_death": Simulates OS process death by backgrounding the app, killing it, and relaunching. No args.
- "toggle_dark_mode": Toggles system dark mode on/off. No args.
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

MODEL_NAME = os.environ.get("GEMINI_MODEL", "gemini-3.5-flash-lite")

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
                model=MODEL_NAME,
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

Based on the run summary and history, generate a JSON diff to update the relational App Map.
CRITICAL INSTRUCTION FOR DEDUPLICATION: You MUST semantically compare any features or bugs you want to log against the `Current App Map`. 
- Do NOT output a feature under `Features_Tested` if a conceptually similar feature already exists for that screen.
- Do NOT output a bug under `New_Bugs` if a conceptually similar bug is already in the `Known_Bugs` list (even if the wording is slightly different).
Only include GENUINELY NOVEL features tested, NOVEL bugs found, and any known bugs that should be marked as "Resolved".
When marking a bug as "Resolved", you MUST use its EXACT `Id` from the Current App Map (e.g., "bug_001"). Do not use descriptions.
Try to accurately map the features and bugs to the specific Screen Name where they occurred.

Output schema:
{{
  "Screens": {{
    "Screen Name": {{
      "Features_Tested": [
        {{ "Name": "New Feature Tested", "Status": "PASS" }}
      ]
    }}
  }},
  "New_Bugs": [
    {{"Id": "bug_XYZ", "Screen": "Screen Name", "Description": "Bug description", "Status": "Open"}}
  ],
  "Resolved_Bugs": ["EXACT 'Id' of the previously Open bug that is now resolved (e.g. 'bug_001')"]
}}

Return ONLY valid JSON. If there are no new items for a category, omit the key or return an empty dict/list.
"""
    try:
        response = client.models.generate_content(
            model=MODEL_NAME,
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
