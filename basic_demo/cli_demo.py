import os
import platform
import sys
import sysconfig


def _ensure_pytorch_cuda_dlls_first():
    """让系统先加载 PyTorch 自带的 CUDA/cuBLAS，避免误用 PhysX 自带的极旧 cudart64_65.dll。"""
    if sys.platform != "win32":
        return
    torch_lib = os.path.join(sysconfig.get_paths()["platlib"], "torch", "lib")
    if not os.path.isdir(torch_lib):
        return
    os.add_dll_directory(torch_lib)
    os.environ["PATH"] = torch_lib + os.pathsep + os.environ.get("PATH", "")


_ensure_pytorch_cuda_dlls_first()

import torch
from transformers import AutoTokenizer, AutoModel

MODEL_PATH = os.environ.get('MODEL_PATH', r'E:\Other\Work\models\ZhipuAI\chatglm3-6b')
TOKENIZER_PATH = os.environ.get("TOKENIZER_PATH", MODEL_PATH)

tokenizer = AutoTokenizer.from_pretrained(TOKENIZER_PATH, trust_remote_code=True)
# RTX 4060 8GB 等显卡无法容纳 FP16 全量权重，INT4 可留在 GPU 上，避免 cpu/disk offload 极慢
if torch.cuda.is_available():
    model = (
        AutoModel.from_pretrained(MODEL_PATH, trust_remote_code=True)
        .quantize(bits=4, device="cuda")
        .cuda()
        .eval()
    )
else:
    model = AutoModel.from_pretrained(MODEL_PATH, trust_remote_code=True, device_map="auto").eval()

os_name = platform.system()
clear_command = 'cls' if os_name == 'Windows' else 'clear'
stop_stream = False

welcome_prompt = "欢迎使用 ChatGLM3-6B 模型，输入内容即可进行对话，clear 清空对话历史，stop 终止程序"


def build_prompt(history):
    prompt = welcome_prompt
    for query, response in history:
        prompt += f"\n\n用户：{query}"
        prompt += f"\n\nChatGLM3-6B：{response}"
    return prompt


def main():
    past_key_values, history = None, []
    global stop_stream
    print(welcome_prompt)
    while True:
        query = input("\n用户：")
        if query.strip() == "stop":
            break
        if query.strip() == "clear":
            past_key_values, history = None, []
            os.system(clear_command)
            print(welcome_prompt)
            continue
        print("\nChatGLM：", end="")
        current_length = 0
        for response, history, past_key_values in model.stream_chat(tokenizer, query, history=history, top_p=1,
                                                                    temperature=0.01,
                                                                    past_key_values=past_key_values,
                                                                    return_past_key_values=True):
            if stop_stream:
                stop_stream = False
                break
            else:
                print(response[current_length:], end="", flush=True)
                current_length = len(response)
        print("")


if __name__ == "__main__":
    main()
