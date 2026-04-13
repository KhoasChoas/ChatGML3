"""
Streamlit demo with two tabs:
1) 中草药智能复核原型（识别 + 药方复核，规则/模拟工具）
2) 原有 ChatGLM 对话演示
"""

import os
import re
import sys
import sysconfig
from collections import Counter


def _ensure_pytorch_cuda_dlls_first():
    """Prefer PyTorch CUDA DLLs on Windows."""
    if sys.platform != "win32":
        return
    torch_lib = os.path.join(sysconfig.get_paths()["platlib"], "torch", "lib")
    if not os.path.isdir(torch_lib):
        return
    os.add_dll_directory(torch_lib)
    os.environ["PATH"] = torch_lib + os.pathsep + os.environ.get("PATH", "")


_ensure_pytorch_cuda_dlls_first()

try:
    import streamlit as st
except ModuleNotFoundError as _e:
    print(
        "未检测到 streamlit。请先安装：\n"
        "  python -m pip install \"streamlit>=1.33.0\"\n"
        "然后运行：\n"
        "  python -m streamlit run web_demo_streamlit.py",
        file=sys.stderr,
    )
    raise SystemExit(1) from _e

import torch
from transformers import AutoModel, AutoTokenizer

MODEL_PATH = os.environ.get("MODEL_PATH", r"E:\Other\Work\models\ZhipuAI\chatglm3-6b")
TOKENIZER_PATH = os.environ.get("TOKENIZER_PATH", MODEL_PATH)

st.set_page_config(page_title="中草药智能复核原型", page_icon=":herb:", layout="wide")

HERB_KEYWORDS = {
    "ginseng": ("人参", 0.89),
    "renshen": ("人参", 0.89),
    "ginger": ("生姜", 0.84),
    "jiang": ("生姜", 0.84),
    "licorice": ("甘草", 0.82),
    "gancao": ("甘草", 0.82),
    "angelica": ("当归", 0.86),
    "danggui": ("当归", 0.86),
}

CONTRAINDICATION_RULES = [
    ("甘草", "海藻", "疑似触发“十八反”配伍禁忌：甘草反海藻。"),
    ("甘草", "大戟", "疑似触发“十八反”配伍禁忌：甘草反大戟。"),
    ("乌头", "半夏", "疑似触发“十八反”配伍禁忌：乌头反半夏。"),
]


def identify_herb_from_filename(filename: str):
    name = filename.lower()
    candidates = []
    for keyword, (herb_name, score) in HERB_KEYWORDS.items():
        if keyword in name:
            candidates.append({"name": herb_name, "confidence": score, "evidence": f"文件名命中关键词: {keyword}"})
    if not candidates:
        return {
            "status": "needs_human_review",
            "top1": None,
            "candidates": [
                {"name": "未知药材", "confidence": 0.28, "evidence": "未命中预置关键词，需药剂师人工确认。"}
            ],
        }
    candidates.sort(key=lambda item: item["confidence"], reverse=True)
    top1 = candidates[0]
    status = "ok" if top1["confidence"] >= 0.8 else "needs_human_review"
    return {"status": status, "top1": top1, "candidates": candidates[:3]}


def parse_prescription(text: str):
    names = []
    for raw in re.split(r"[，,;；\n]+", text.strip()):
        token = raw.strip()
        if not token:
            continue
        name = re.sub(r"\d+(\.\d+)?\s*(g|克|mg|两)?", "", token, flags=re.IGNORECASE).strip()
        if name:
            names.append(name)
    return names


def review_prescription(names):
    issues = []
    counter = Counter(names)
    for herb, count in counter.items():
        if count > 1:
            issues.append({"level": "warn", "detail": f"{herb} 重复出现 {count} 次，请确认是否重复录入。"})

    present = set(names)
    for left, right, msg in CONTRAINDICATION_RULES:
        if left in present and right in present:
            issues.append({"level": "error", "detail": msg})

    if not issues:
        issues.append({"level": "info", "detail": "未命中预置风险规则（仅原型规则，非医疗结论）。"})
    return issues


@st.cache_resource
def get_model():
    tokenizer = AutoTokenizer.from_pretrained(TOKENIZER_PATH, trust_remote_code=True)
    if torch.cuda.is_available():
        model = (
            AutoModel.from_pretrained(MODEL_PATH, trust_remote_code=True)
            .quantize(bits=4, device="cuda")
            .cuda()
            .eval()
        )
    else:
        model = AutoModel.from_pretrained(MODEL_PATH, trust_remote_code=True, device_map="auto").eval()
    return tokenizer, model


tab_review, tab_chat = st.tabs(["中草药智能复核预览", "ChatGLM 对话"])

with tab_review:
    st.subheader("中草药识别（原型）")
    upload = st.file_uploader("上传中草药图片（通过文件名关键词做模拟识别）", type=["png", "jpg", "jpeg", "webp"])
    if upload is not None:
        result = identify_herb_from_filename(upload.name)
        st.image(upload, caption=f"上传文件: {upload.name}", use_container_width=True)
        if result["status"] == "ok":
            st.success(f"识别结果: {result['top1']['name']} (置信度 {result['top1']['confidence']:.2f})")
        else:
            st.warning("识别置信度不足，建议进入药剂师人工复核。")
        st.markdown("候选结果:")
        st.json(result["candidates"])

    st.divider()
    st.subheader("药方复核（原型）")
    st.caption("示例输入：甘草6g，海藻10g，当归12g")
    rx_input = st.text_area("请输入药方（药名+剂量，逗号或换行分隔）", height=120)
    if st.button("执行药方复核", type="primary"):
        herbs = parse_prescription(rx_input)
        if not herbs:
            st.error("未识别到任何药名，请检查输入格式。")
        else:
            st.write("解析到的药材：", herbs)
            issues = review_prescription(herbs)
            for issue in issues:
                if issue["level"] == "error":
                    st.error(issue["detail"])
                elif issue["level"] == "warn":
                    st.warning(issue["detail"])
                else:
                    st.info(issue["detail"])

    st.caption("说明：当前为预览原型，识别和复核逻辑为规则/模拟实现，后续可替换为真实视觉模型和知识库工具。")

with tab_chat:
    st.subheader("ChatGLM 对话演示")
    if "history" not in st.session_state:
        st.session_state.history = []
    if "past_key_values" not in st.session_state:
        st.session_state.past_key_values = None

    max_length = st.sidebar.slider("max_length", 0, 32768, 8192, step=1)
    top_p = st.sidebar.slider("top_p", 0.0, 1.0, 0.8, step=0.01)
    temperature = st.sidebar.slider("temperature", 0.0, 1.0, 0.6, step=0.01)

    if st.sidebar.button("清理会话历史", key="clean"):
        st.session_state.history = []
        st.session_state.past_key_values = None
        if torch.cuda.is_available():
            torch.cuda.empty_cache()
        st.rerun()

    for message in st.session_state.history:
        if message["role"] == "user":
            with st.chat_message(name="user", avatar="user"):
                st.markdown(message["content"])
        else:
            with st.chat_message(name="assistant", avatar="assistant"):
                st.markdown(message["content"])

    with st.chat_message(name="user", avatar="user"):
        input_placeholder = st.empty()
    with st.chat_message(name="assistant", avatar="assistant"):
        message_placeholder = st.empty()

    prompt_text = st.chat_input("请输入您的问题")
    if prompt_text:
        tokenizer, model = get_model()
        input_placeholder.markdown(prompt_text)
        history = st.session_state.history
        past_key_values = st.session_state.past_key_values
        for response, history, past_key_values in model.stream_chat(
            tokenizer,
            prompt_text,
            history,
            past_key_values=past_key_values,
            max_length=max_length,
            top_p=top_p,
            temperature=temperature,
            return_past_key_values=True,
        ):
            message_placeholder.markdown(response)
        st.session_state.history = history
        st.session_state.past_key_values = past_key_values
