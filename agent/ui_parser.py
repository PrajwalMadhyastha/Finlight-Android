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
    
    def process_node(node):
        clickable = node.get("clickable") == "true"
        scrollable = node.get("scrollable") == "true"
        bounds_str = node.get("bounds", "")
        
        if clickable:
            text_parts = []
            def gather_text(n):
                t = n.get("text", "")
                d = n.get("content-desc", "")
                if t: text_parts.append(t)
                if d and d not in text_parts: text_parts.append(d)
                for child in n:
                    gather_text(child)
            
            gather_text(node)
            combined_text = " ".join(text_parts).strip()
            
            if bounds_str:
                bounds = parse_bounds(bounds_str)
                if bounds and bounds["width"] > 0 and bounds["height"] > 0:
                    res_id = node.get("resource-id", "")
                    el = {
                        "text": combined_text if combined_text else None,
                        "id": res_id.split("/")[-1] if "/" in res_id else (res_id if res_id else None),
                        "clickable": True,
                        "scrollable": scrollable,
                        "center": [bounds["center_x"], bounds["center_y"]]
                    }
                    el = {k: v for k, v in el.items() if v is not None and v != ""}
                    elements.append(el)
            
            # Since this node is clickable, its children's texts are merged here. 
            # We don't process children as standalone interactive elements.
            return

        text = node.get("text", "")
        content_desc = node.get("content-desc", "")
        is_interesting = (text or content_desc or scrollable)
        
        if is_interesting and bounds_str:
            bounds = parse_bounds(bounds_str)
            if bounds and bounds["width"] > 0 and bounds["height"] > 0:
                res_id = node.get("resource-id", "")
                el = {
                    "text": text if text else None,
                    "desc": content_desc if content_desc else None,
                    "id": res_id.split("/")[-1] if "/" in res_id else (res_id if res_id else None),
                    "clickable": False,
                    "scrollable": scrollable,
                    "center": [bounds["center_x"], bounds["center_y"]]
                }
                el = {k: v for k, v in el.items() if v is not None and v != ""}
                elements.append(el)
                
        for child in node:
            process_node(child)

    process_node(root)
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
