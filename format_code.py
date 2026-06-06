import os
import re

DIR = r'D:\dev\RemSoundAndroid\app\src\main\java\rem\receiver\android'

for root, _, files in os.walk(DIR):
    for f in files:
        if f.endswith('.kt') or f.endswith('.java'):
            filepath = os.path.join(root, f)
            try:
                with open(filepath, 'r', encoding='utf-8') as file:
                    lines = file.readlines()
                
                new_lines = []
                for line in lines:
                    # Strip single-line comments (ignoring // inside strings for simplicity, this is just basic)
                    # For safety, we only remove // if it's not part of a URL, but for Kotlin code it's mostly fine
                    if '//' in line and not 'http' in line:
                        line = line.split('//')[0] + '\n'
                    if line.strip() == '':
                        if len(new_lines) > 0 and new_lines[-1].strip() == '':
                            continue # skip multiple empty lines
                        
                    # Convert 4 spaces to 1 tab for leading whitespace
                    leading_spaces = len(line) - len(line.lstrip(' '))
                    if leading_spaces > 0:
                        tabs = leading_spaces // 4
                        remainder = leading_spaces % 4
                        line = '\t' * tabs + ' ' * remainder + line.lstrip(' ')
                        
                    new_lines.append(line)
                
                # Use a safe write strategy
                temp_filepath = filepath + '.tmp'
                with open(temp_filepath, 'w', encoding='utf-8') as file:
                    file.writelines(new_lines)
                
                # Replace the original file
                os.replace(temp_filepath, filepath)
                print(f"Formatted: {filepath}")
            except Exception as e:
                print(f"Error processing {filepath}: {e}")

print("Formatting complete.")
