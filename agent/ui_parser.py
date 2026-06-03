import subprocess
import xml.etree.ElementTree as ET
import json
import re

def dump_ui_tree() -> str:
    """Dumps the UI xml via adb and reads it."""
    # Run uiautomator dump
    subprocess.run(["adb", "shell", "uiautomator", "dump", "/data/local/tmp/window_dump.xml"], capture_output=True, check=True)
    # Pull the file
    subprocess.run(["adb", "pull", "/data/local/tmp/window_dump.xml", "window_dump.xml"], capture_output=True, check=True)
    
    with open("window_dump.xml", "r", encoding="utf-8") as f:
        return f.read()

def parse_bounds(bounds_str: str):
    """Parses bounds string like '[0,84][1080,2400]' into center x,y."""
    match = re.match(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', bounds_str)
    if not match:
        return None
    x1, y1, x2, y2 = map(int, match.groups())
    center_x = (x1 + x2) // 2
    center_y = (y1 + y2) // 2
    return {"center_x": center_x, "center_y": center_y, "width": x2 - x1, "height": y2 - y1}

def get_interactable_elements(xml_content: str):
    """Parses XML and returns a list of dictionaries of interactable elements."""
    root = ET.fromstring(xml_content)
    elements = []
    
    # Iterate all nodes
    for node in root.iter('node'):
        # We only care if it has text, content-desc, or is clickable/focusable
        text = node.get("text", "")
        content_desc = node.get("content-desc", "")
        resource_id = node.get("resource-id", "")
        clickable = node.get("clickable") == "true"
        focusable = node.get("focusable") == "true"
        scrollable = node.get("scrollable") == "true"
        bounds_str = node.get("bounds", "")
        
        # Determine if it's "interesting" to the LLM
        is_interesting = (text or content_desc or clickable or scrollable)
        
        if is_interesting and bounds_str:
            bounds = parse_bounds(bounds_str)
            if bounds:
                # If width or height is 0, skip
                if bounds["width"] <= 0 or bounds["height"] <= 0:
                    continue
                    
                el = {
                    "text": text if text else None,
                    "desc": content_desc if content_desc else None,
                    "id": resource_id.split("/")[-1] if "/" in resource_id else resource_id,
                    "clickable": clickable,
                    "scrollable": scrollable,
                    "center": [bounds["center_x"], bounds["center_y"]]
                }
                # Clean up None values
                el = {k: v for k, v in el.items() if v is not None and v != ""}
                elements.append(el)
                
    return elements

def get_ui_json():
    """High level function to get current screen as compact JSON."""
    try:
        xml = dump_ui_tree()
        elements = get_interactable_elements(xml)
        return json.dumps(elements, indent=2)
    except Exception as e:
        return json.dumps({"error": f"Failed to get UI: {str(e)}"})

if __name__ == "__main__":
    print(get_ui_json())
