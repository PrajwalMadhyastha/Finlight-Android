import google.generativeai as genai
import os
import json

SYSTEM_PROMPT = """You are an autonomous exploratory Android QA tester.
Your goal is to explore the app, test features, and find bugs based on a given HIGH-LEVEL GOAL.

You will be provided with:
1. The HIGH-LEVEL GOAL.
2. The CURRENT UI STATE (a JSON array of interactable elements on the screen, with their center coordinates).
3. The ACTION HISTORY (what you have done so far).

You must think step-by-step (ReAct strategy) and output your next action in strict JSON format.

Supported actions:
- "tap": Taps a specific coordinate. Requires "x" and "y" in args.
- "type": Types text. Requires "text" in args. (Note: you must tap a text field before typing).
- "swipe": Swipes on the screen. Requires "x1", "y1", "x2", "y2" in args.
- "back": Presses the hardware back button. No args.
- "home": Presses the hardware home button. No args.
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
    genai.configure(api_key=api_key)
    # Use gemini-3.5-flash for cost/speed
    model = genai.GenerativeModel('gemini-3.5-flash', system_instruction=SYSTEM_PROMPT)
    return model

def get_next_action(model, goal: str, ui_state: str, history: list) -> dict:
    prompt = f"HIGH-LEVEL GOAL:\n{goal}\n\n"
    prompt += f"ACTION HISTORY:\n{json.dumps(history, indent=2)}\n\n"
    prompt += f"CURRENT UI STATE:\n{ui_state}\n\n"
    prompt += "What is your next action? Respond in JSON format only."
    
    response = model.generate_content(prompt)
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
