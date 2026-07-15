import os
import glob

html_files = glob.glob('frontend/*.html')

for file in html_files:
    with open(file, 'r', encoding='utf-8') as f:
        content = f.read()
    
    # Fix the botched replace
    content = content.replace('<script src="/config.js"></script>`r`n    <script src="/auth.js"></script>', '<script src="/config.js"></script>\n    <script src="/auth.js"></script>')
    
    # Check if auth.js is even there, if not, but it's a page that needs config.js (like login, signup, index), add it before closing head
    if '<script src="/config.js"></script>' not in content:
        content = content.replace('</head>', '    <script src="/config.js"></script>\n</head>')
        
    with open(file, 'w', encoding='utf-8') as f:
        f.write(content)
