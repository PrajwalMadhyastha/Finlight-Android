import os
import json
from google import genai
from google.genai import types

def main():
    memory_text = ""
    
    if os.path.exists("app_map.json"):
        with open("app_map.json", "r", encoding="utf-8") as f:
            try:
                data = json.load(f)
                if "Metadata" in data and "Screens" in data:
                    print("app_map.json is already in the new format. No migration needed.")
                    return
                # It's the old format, we need to migrate it
                memory_text = json.dumps(data)
                print("Found old format app_map.json. Migrating...")
            except json.JSONDecodeError:
                pass
                
    if not memory_text and os.path.exists("agent_memory.txt"):
        with open("agent_memory.txt", "r", encoding="utf-8") as f:
            memory_text = f.read()
            print("Found agent_memory.txt. Migrating...")

    if not memory_text.strip():
        print("No old memory found. Creating empty relational app_map.json.")
        create_empty_app_map()
        return

    print("Parsing agent_memory.txt with Gemini to extract App Map...")
    api_key = os.environ.get("GEMINI_API_KEY")
    if not api_key:
        print("ERROR: GEMINI_API_KEY environment variable not set.")
        return
        
    client = genai.Client(api_key=api_key)
    
    prompt = f"""
You are an expert at parsing raw testing logs or old flat JSON app maps. We are migrating our testing agent to use a relational structured "App Map".
Here is the raw data from the previous test runs:
{memory_text}

Extract and reorganize the information into the following JSON schema. Try to infer which features were tested on which screens. If you cannot determine the screen for a feature or bug, place it under an "Uncategorized" screen or leave the Screen field blank for bugs.

{{
  "Metadata": {{
    "App_Version": "Unknown",
    "API_Level": "Unknown",
    "Execution_Time": "0 seconds"
  }},
  "Screens": {{
    "Screen Name": {{
      "Features_Tested": [
        {{
          "Name": "Feature Name",
          "Status": "PASS"
        }}
      ]
    }}
  }},
  "Known_Bugs": [
    {{
      "Id": "bug_001",
      "Screen": "Screen Name",
      "Description": "Crash on transfer",
      "Status": "Open"
    }}
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
        "Metadata": { "App_Version": "Unknown", "API_Level": "Unknown", "Execution_Time": "0 seconds" },
        "Screens": {},
        "Known_Bugs": []
    }
    with open("app_map.json", "w", encoding="utf-8") as f:
        json.dump(empty_map, f, indent=2)

if __name__ == "__main__":
    main()
