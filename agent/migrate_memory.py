import os
import json
from google import genai
from google.genai import types

def main():
    if not os.path.exists("agent_memory.txt"):
        print("agent_memory.txt not found. Creating empty app_map.json.")
        create_empty_app_map()
        return

    with open("agent_memory.txt", "r", encoding="utf-8") as f:
        memory_text = f.read()

    if not memory_text.strip():
        print("agent_memory.txt is empty. Creating empty app_map.json.")
        create_empty_app_map()
        return

    print("Parsing agent_memory.txt with Gemini to extract App Map...")
    api_key = os.environ.get("GEMINI_API_KEY")
    if not api_key:
        print("ERROR: GEMINI_API_KEY environment variable not set.")
        return
        
    client = genai.Client(api_key=api_key)
    
    prompt = f"""
You are an expert at parsing raw testing logs. We are migrating our testing agent to use a structured "App Map".
Here is the raw text from the previous test runs:
{memory_text}

Extract the information into the following JSON schema:
{{
  "Screens_Visited": ["Screen 1", "Screen 2"],
  "Features_Tested": ["Feature 1", "Feature 2"],
  "Known_Bugs": [
    {{"Description": "Crash on transfer", "Status": "Open"}}
  ]
}}

Return ONLY valid JSON. If there are no bugs, return an empty list for Known_Bugs.
"""

    response = client.models.generate_content(
        model='gemini-3.1-flash-lite',
        contents=prompt,
        config=types.GenerateContentConfig(
            temperature=0.0
        )
    )

    text_response = response.text.strip()
    if text_response.startswith("```json"):
        text_response = text_response[7:]
    elif text_response.startswith("```"):
        text_response = text_response[3:]
    if text_response.endswith("```"):
        text_response = text_response[:-3]
        
    text_response = text_response.strip()
    
    try:
        app_map = json.loads(text_response)
        with open("app_map.json", "w", encoding="utf-8") as f:
            json.dump(app_map, f, indent=2)
        print("Successfully created app_map.json.")
        print("You can now safely delete agent_memory.txt.")
    except json.JSONDecodeError:
        print(f"Failed to parse JSON response: {text_response}")
        create_empty_app_map()

def create_empty_app_map():
    empty_map = {
        "Screens_Visited": [],
        "Features_Tested": [],
        "Known_Bugs": []
    }
    with open("app_map.json", "w", encoding="utf-8") as f:
        json.dump(empty_map, f, indent=2)

if __name__ == "__main__":
    main()
