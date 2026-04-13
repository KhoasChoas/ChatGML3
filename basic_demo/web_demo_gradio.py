"""
This script creates an interactive web demo for the ChatGLM3-6B model using Gradio,
a Python library for building quick and easy UI components for machine learning models.
It's designed to showcase the capabilities of the ChatGLM3-6B model in a user-friendly interface,
allowing users to interact with the model through a chat-like interface.

Usage:
- Run the script to start the Gradio web server.
- Interact with the model by typing questions and receiving responses.

Requirements:
- Gradio (required for 4.13.0 and later, 3.x is not support now) should be installed.

Note: The script includes a modification to the Chatbot's postprocess method to handle markdown to HTML conversion,
ensuring that the chat interface displays formatted text correctly.

"""

import os
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

try:
    import gradio as gr
except ModuleNotFoundError as _e:
    print(
        "未检测到 gradio。请在已激活的虚拟环境中安装后再运行本脚本，例如：\n"
        "  python -m pip install \"gradio>=4.26.0\"\n"
        "（本仓库文档要求 Gradio 4.13+；若界面 API 报错，可尝试 4.x："
        "python -m pip install \"gradio>=4.44.0,<5\"）",
        file=sys.stderr,
    )
    raise SystemExit(1) from _e

import torch
from threading import Thread

from typing import Union
from pathlib import Path
from transformers import (
    AutoModelForCausalLM,
    AutoTokenizer,
    PreTrainedModel,
    PreTrainedTokenizer,
    PreTrainedTokenizerFast,
    StoppingCriteria,
    StoppingCriteriaList,
    TextIteratorStreamer
)

import socket

ModelType = PreTrainedModel
TokenizerType = Union[PreTrainedTokenizer, PreTrainedTokenizerFast]

MODEL_PATH = os.environ.get("MODEL_PATH", r"E:\Other\Work\models\ZhipuAI\chatglm3-6b")
TOKENIZER_PATH = os.environ.get("TOKENIZER_PATH", MODEL_PATH)


def _resolve_path(path: Union[str, Path]) -> Path:
    return Path(path).expanduser().resolve()


def load_model_and_tokenizer(
        model_dir: Union[str, Path], trust_remote_code: bool = True
) -> tuple[ModelType, TokenizerType]:
    model_dir = _resolve_path(model_dir)
    if (model_dir / 'adapter_config.json').exists():
        try:
            from peft import AutoPeftModelForCausalLM
        except ModuleNotFoundError as _e:
            print(
                "检测到 LoRA/PEFT 目录（存在 adapter_config.json），需要安装 peft：\n"
                "  python -m pip install peft",
                file=sys.stderr,
            )
            raise SystemExit(1) from _e
        model = AutoPeftModelForCausalLM.from_pretrained(
            model_dir, trust_remote_code=trust_remote_code, device_map='auto'
        )
        tokenizer_dir = model.peft_config['default'].base_model_name_or_path
    else:
        if torch.cuda.is_available():
            model = (
                AutoModelForCausalLM.from_pretrained(model_dir, trust_remote_code=trust_remote_code)
                .quantize(bits=4, device="cuda")
                .cuda()
                .eval()
            )
        else:
            model = AutoModelForCausalLM.from_pretrained(
                model_dir, trust_remote_code=trust_remote_code, device_map='auto'
            ).eval()
        tokenizer_dir = _resolve_path(TOKENIZER_PATH)
    tokenizer = AutoTokenizer.from_pretrained(
        tokenizer_dir, trust_remote_code=trust_remote_code
    )
    return model, tokenizer


model, tokenizer = load_model_and_tokenizer(MODEL_PATH, trust_remote_code=True)


class StopOnTokens(StoppingCriteria):
    def __call__(self, input_ids: torch.LongTensor, scores: torch.FloatTensor, **kwargs) -> bool:
        stop_ids = [0, 2]
        for stop_id in stop_ids:
            if input_ids[0][-1] == stop_id:
                return True
        return False


def parse_text(text):
    lines = text.split("\n")
    lines = [line for line in lines if line != ""]
    count = 0
    for i, line in enumerate(lines):
        if "```" in line:
            count += 1
            items = line.split('`')
            if count % 2 == 1:
                lines[i] = f'<pre><code class="language-{items[-1]}">'
            else:
                lines[i] = f'<br></code></pre>'
        else:
            if i > 0:
                if count % 2 == 1:
                    line = line.replace("`", "\`")
                    line = line.replace("<", "&lt;")
                    line = line.replace(">", "&gt;")
                    line = line.replace(" ", "&nbsp;")
                    line = line.replace("*", "&ast;")
                    line = line.replace("_", "&lowbar;")
                    line = line.replace("-", "&#45;")
                    line = line.replace(".", "&#46;")
                    line = line.replace("!", "&#33;")
                    line = line.replace("(", "&#40;")
                    line = line.replace(")", "&#41;")
                    line = line.replace("$", "&#36;")
                lines[i] = "<br>" + line
    text = "".join(lines)
    return text


def _gradio_content_to_plain(content) -> str:
    """Gradio 6：Chatbot 的 content 常为 [{'type': 'text', 'text': '...'}]，与旧版纯 str 兼容。"""
    if content is None:
        return ""
    if isinstance(content, str):
        return content
    if isinstance(content, list):
        parts = []
        for block in content:
            if isinstance(block, dict):
                parts.append(block.get("text") or "")
            elif isinstance(block, str):
                parts.append(block)
        return "".join(parts)
    return str(content)


def _gradio_text_message(role: str, text: str) -> dict:
    return {"role": role, "content": [{"type": "text", "text": text}]}


def _append_token_to_last_assistant(history, token: str) -> None:
    last = history[-1]
    if last.get("role") != "assistant":
        return
    c = last.get("content")
    if isinstance(c, list) and c and isinstance(c[0], dict):
        c[0]["text"] = (c[0].get("text") or "") + token
    elif isinstance(c, str):
        last["content"] = c + token
    else:
        last["content"] = [{"type": "text", "text": token}]


def predict(history, max_length, top_p, temperature, system_prompt):
    stop = StopOnTokens()
    messages = []
    if system_prompt != "":
        messages.append({"role": "system", "content": system_prompt})

    # Gradio 6：Chatbot 值为 messages 格式 [{"role","content"}, ...]，不再是 [user, bot] 二元组
    if not history:
        return
    for m in history:
        role = m.get("role")
        content = _gradio_content_to_plain(m.get("content"))
        if role == "assistant" and not content:
            continue
        if role in ("user", "assistant"):
            messages.append({"role": role, "content": content})

    print("\n\n====conversation====\n", messages)
    model_inputs = tokenizer.apply_chat_template(messages,
                                                 add_generation_prompt=True,
                                                 tokenize=True,
                                                 return_tensors="pt").to(next(model.parameters()).device)
    streamer = TextIteratorStreamer(tokenizer, timeout=60, skip_prompt=True, skip_special_tokens=True)
    generate_kwargs = {
        "input_ids": model_inputs,
        "streamer": streamer,
        "max_new_tokens": max_length,
        "do_sample": True,
        "top_p": top_p,
        "temperature": temperature,
        "stopping_criteria": StoppingCriteriaList([stop]),
        "repetition_penalty": 1.2,
    }
    t = Thread(target=model.generate, kwargs=generate_kwargs)
    t.start()

    for new_token in streamer:
        if new_token != '':
            _append_token_to_last_assistant(history, new_token)
            yield history


with gr.Blocks(title="ChatGLM") as demo:
    gr.Markdown("## ChatGLM3-6B")

    with gr.Row():
        with gr.Column(scale=4):
            chatbot = gr.Chatbot(layout="panel")
            with gr.Column(scale=12):
                user_input = gr.Textbox(show_label=False, placeholder="Input to chat...", lines=3, container=False)
            with gr.Column(min_width=32, scale=1):
                submitBtn = gr.Button("Submit", variant="primary")
        with gr.Column(scale=1):
            emptyBtn = gr.Button("Clear History")
            max_length = gr.Slider(0, 32768, value=16384, step=1.0, label="Maximum length", interactive=True)
            top_p = gr.Slider(0, 1, value=0.8, step=0.01, label="Top P", interactive=True)
            temperature = gr.Slider(0.01, 1, value=0.6, step=0.01, label="Temperature", interactive=True)
            gr.HTML("""<span>System Prompt</span>""")
            system_prompt = gr.Textbox(show_label=False, placeholder="System Prompt", lines=6, container=False)

    def user(query, history):
        history = list(history) if history else []
        return "", history + [
            _gradio_text_message("user", parse_text(query)),
            _gradio_text_message("assistant", ""),
        ]

    submitBtn.click(user, [user_input, chatbot], [user_input, chatbot], queue=False).then(
        predict, [chatbot, max_length, top_p, temperature, system_prompt], chatbot
    )
    emptyBtn.click(lambda: [], None, chatbot, queue=False)

demo.queue()
demo.launch(server_name=socket.gethostbyname(socket.gethostname()), server_port=7870, inbrowser=True, share=False)
