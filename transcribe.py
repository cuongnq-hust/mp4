import sys
import requests
import json
import codecs

# Ép console in ra chuẩn UTF-8 để Java đọc không bị lỗi font
sys.stdout = codecs.getwriter("utf-8")(sys.stdout.detach())

# 1. ĐIỀN API KEY GROQ CỦA BẠN VÀO ĐÂY (Key bắt đầu bằng gsk_...)
API_KEY = "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"

def transcribe_audio(audio_path):
    url = "https://api.groq.com/openai/v1/audio/transcriptions"
    
    headers = {
        "Authorization": f"Bearer {API_KEY}"
    }
    
    # Sử dụng mô hình lớn nhất whisper-large-v3 để xử lý giọng địa phương
    with open(audio_path, "rb") as file:
        files = {
            "file": (audio_path, file),
            "model": (None, "whisper-large-v3"),
            "response_format": (None, "verbose_json"),
            "language": (None, "zh") # Ép nhận diện tiếng Trung để tăng độ chuẩn xác
        }
        
        response = requests.post(url, headers=headers, files=files)
        
        if response.status_code != 200:
            print(f"Lỗi API: {response.text}", file=sys.stderr)
            sys.exit(1)
            
        data = response.json()
        
        # In ra màn hình theo đúng định dạng | Java yêu cầu
        for segment in data.get("segments", []):
            start = f"{segment['start']:.2f}"
            end = f"{segment['end']:.2f}"
            text = segment['text'].strip()
            # Xóa các ký tự | vô tình có trong câu để không làm hỏng split của Java
            text = text.replace("|", "") 
            print(f"{start}|{end}|{text}")

if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("Vui lòng cung cấp đường dẫn file âm thanh.", file=sys.stderr)
        sys.exit(1)
        
    audio_file_path = sys.argv[1]
    transcribe_audio(audio_file_path)