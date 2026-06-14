import json
import os
import sys

def convert_file(filepath):
    print(f"Reading: {filepath}")
    with open(filepath, 'r', encoding='utf-8') as f:
        content = f.read()
    print(f"Original first 200 chars: {content[:200]}")
    
    data = json.loads(content)
    
    with open(filepath, 'w', encoding='utf-8', newline='') as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
        f.write('\n')
    
    # Verify
    with open(filepath, 'r', encoding='utf-8') as f:
        content2 = f.read()
    print(f"Converted first 200 chars: {content2[:200]}")
    print("Done!")

if __name__ == '__main__':
    path = sys.argv[1] if len(sys.argv) > 1 else r'src\main\resources\assets\advancedatamonitor\config\assistant-lexicon.json'
    convert_file(path)
