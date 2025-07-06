#!/usr/bin/env python3
"""
Test Firebase Storage access to verify permissions are working
"""
import requests
import json

# Firebase Storage REST API endpoint
bucket_name = "geoai-2e3da.firebasestorage.app"
folder_path = "llm"

# List files in the folder using REST API
url = f"https://firebasestorage.googleapis.com/v0/b/{bucket_name}/o"
params = {
    "prefix": f"{folder_path}/",
    "delimiter": "/"
}

print(f"Testing Firebase Storage access...")
print(f"Bucket: gs://{bucket_name}")
print(f"Folder: {folder_path}")
print(f"URL: {url}")
print("-" * 50)

try:
    response = requests.get(url, params=params)
    print(f"Status Code: {response.status_code}")
    
    if response.status_code == 200:
        data = response.json()
        
        if "items" in data:
            print(f"\nFound {len(data['items'])} files in {folder_path}/:")
            for item in data['items']:
                print(f"  - {item['name']}")
                print(f"    Size: {item.get('size', 'Unknown')} bytes")
                print(f"    Type: {item.get('contentType', 'Unknown')}")
        else:
            print(f"\nNo files found in {folder_path}/ folder")
            
    elif response.status_code == 403:
        print("\n❌ Permission Denied (403)")
        print("Firebase Storage rules are still blocking access.")
        print(response.text)
    else:
        print(f"\n❌ Error: {response.status_code}")
        print(response.text)
        
except Exception as e:
    print(f"\n❌ Error connecting to Firebase Storage: {e}")

print("\n" + "-" * 50)
print("Note: If you see 403 errors, update your Firebase Storage rules.")