"""
Streamlit demo with two tabs:
1) 中草药智能复核原型（识别 + 药方复核，规则/模拟工具）
2) 原有 ChatGLM 对话演示
"""

import os
import re
import sys
import sysconfig
import json
from collections import Counter
from datetime import datetime
from pathlib import Path
from uuid import uuid4


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

HERB_KNOWLEDGE = {
    "人参": {"功效": "大补元气，补脾益肺，生津养血", "注意事项": "实热证慎用"},
    "生姜": {"功效": "解表散寒，温中止呕", "注意事项": "阴虚内热者慎用"},
    "甘草": {"功效": "补脾益气，缓急止痛，调和诸药", "注意事项": "长期大剂量可能引起水钠潴留"},
    "当归": {"功效": "补血活血，调经止痛，润肠通便", "注意事项": "湿盛中满者慎用"},
}

IMAGE_LIBRARY = {
    "人参": ["renshen_batch_a_01.jpg", "renshen_batch_b_03.jpg"],
    "生姜": ["ginger_cut_01.jpg", "ginger_slice_07.jpg"],
    "甘草": ["gancao_piece_02.jpg", "licorice_root_11.jpg"],
    "当归": ["danggui_slice_01.jpg", "angelica_root_09.jpg"],
}

CONTRAINDICATION_RULES = [
    ("甘草", "海藻", "疑似触发“十八反”配伍禁忌：甘草反海藻。"),
    ("甘草", "大戟", "疑似触发“十八反”配伍禁忌：甘草反大戟。"),
    ("乌头", "半夏", "疑似触发“十八反”配伍禁忌：乌头反半夏。"),
]

DEMO_DATA_DIR = Path(__file__).resolve().parent / "demo_data"
DEMO_DATA_DIR.mkdir(parents=True, exist_ok=True)
DISPENSE_LOG_PATH = DEMO_DATA_DIR / "dispense_log.json"
ERROR_REPORT_PATH = DEMO_DATA_DIR / "error_report_log.json"


def load_dispense_logs():
    if not DISPENSE_LOG_PATH.exists():
        return []
    try:
        with DISPENSE_LOG_PATH.open("r", encoding="utf-8") as f:
            data = json.load(f)
    except (json.JSONDecodeError, OSError):
        return []
    if isinstance(data, list):
        return data
    return []


def save_dispense_logs(logs):
    with DISPENSE_LOG_PATH.open("w", encoding="utf-8") as f:
        json.dump(logs, f, ensure_ascii=False, indent=2)


def load_error_reports():
    if not ERROR_REPORT_PATH.exists():
        return []
    try:
        with ERROR_REPORT_PATH.open("r", encoding="utf-8") as f:
            data = json.load(f)
    except (json.JSONDecodeError, OSError):
        return []
    if isinstance(data, list):
        return data
    return []


def save_error_reports(reports):
    with ERROR_REPORT_PATH.open("w", encoding="utf-8") as f:
        json.dump(reports, f, ensure_ascii=False, indent=2)


def create_error_report(trigger, user, image_name, note, model_candidates):
    reports = load_error_reports()
    entry = {
        "report_id": f"ER-{uuid4().hex[:8]}",
        "timestamp": datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
        "trigger": trigger,
        "status": "pending",
        "from_user_id": (user or {}).get("id", "guest"),
        "from_user_name": (user or {}).get("name", "访客"),
        "image_name": image_name,
        "note": note,
        "model_candidates": model_candidates,
        "corrected_herb": "",
        "resolved_by": "",
    }
    reports.append(entry)
    save_error_reports(reports)
    return entry


def parse_pharmacist_qr(qr_text: str):
    """
    Demo format:
    PHARM|id=P001|name=张三
    """
    raw = qr_text.strip()
    if not raw:
        return None, "二维码内容为空。"
    parts = [item.strip() for item in raw.split("|") if item.strip()]
    if len(parts) < 3 or parts[0].upper() != "PHARM":
        return None, "药剂师二维码格式错误，示例：PHARM|id=P001|name=张三"
    info = {}
    for token in parts[1:]:
        if "=" not in token:
            continue
        k, v = token.split("=", 1)
        info[k.strip().lower()] = v.strip()
    pharmacist_id = info.get("id")
    pharmacist_name = info.get("name")
    if not pharmacist_id or not pharmacist_name:
        return None, "药剂师二维码缺少 id 或 name。"
    return {"id": pharmacist_id, "name": pharmacist_name}, None


def parse_prescription_qr(qr_text: str):
    """
    Demo format:
    RX|id=RX20260413-001|items=甘草6g,海藻10g,当归12g
    """
    raw = qr_text.strip()
    if not raw:
        return None, "二维码内容为空。"
    parts = [item.strip() for item in raw.split("|") if item.strip()]
    if len(parts) < 3 or parts[0].upper() != "RX":
        return None, "处方二维码格式错误，示例：RX|id=RX001|items=甘草6g,海藻10g"
    info = {}
    for token in parts[1:]:
        if "=" not in token:
            continue
        k, v = token.split("=", 1)
        info[k.strip().lower()] = v.strip()
    rx_id = info.get("id", f"RX-{uuid4().hex[:8]}")
    items_text = info.get("items", "")
    if not items_text:
        return None, "处方二维码缺少 items 字段。"
    return {"rx_id": rx_id, "items_text": items_text}, None


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


def tool_search_image_library(herb_name: str):
    return IMAGE_LIBRARY.get(herb_name, [])


def tool_lookup_herb_knowledge(herb_name: str):
    return HERB_KNOWLEDGE.get(herb_name, {"功效": "暂无知识条目", "注意事项": "暂无注意事项"})


def run_herb_agent(image_name: str):
    tool_calls = []
    identify_result = identify_herb_from_filename(image_name)
    tool_calls.append(
        {
            "tool": "identify_herb_from_image",
            "input": {"image_name": image_name},
            "output": identify_result,
        }
    )

    if identify_result["top1"]:
        herb_name = identify_result["top1"]["name"]
        image_hits = tool_search_image_library(herb_name)
        tool_calls.append(
            {
                "tool": "search_herb_image_library",
                "input": {"herb_name": herb_name},
                "output": {"top_images": image_hits},
            }
        )
        knowledge = tool_lookup_herb_knowledge(herb_name)
        tool_calls.append(
            {
                "tool": "lookup_herb_knowledge",
                "input": {"herb_name": herb_name},
                "output": knowledge,
            }
        )
    return {"identify_result": identify_result, "tool_calls": tool_calls}


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


def count_by_pharmacist(logs):
    result = {}
    for item in logs:
        key = f"{item.get('pharmacist_name', '未知')}({item.get('pharmacist_id', '-')})"
        result[key] = result.get(key, 0) + 1
    return result


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


if "user" not in st.session_state:
    st.session_state.user = None
if "page" not in st.session_state:
    st.session_state.page = "home"


def render_home_page():
    left_col, right_col = st.columns([8, 1])
    with left_col:
        st.title("中草药智能复核系统（预览）")
    with right_col:
        st.write("")
        if st.session_state.user:
            if st.button("退出登录", key="home_logout"):
                st.session_state.user = None
                st.rerun()
        else:
            if st.button("登录", key="home_login"):
                st.session_state.page = "login"
                st.rerun()

    current_user = st.session_state.user
    if current_user:
        st.success(f"当前用户：{current_user['name']}（角色：{current_user['role']}）")
    else:
        st.info("当前为访客状态。右上角点击“登录”可进入登录页。")

    st.subheader("中草药智能体识别（全用户可用）")
    st.caption("智能体会自主调用：图像识别 tool -> 图像库检索 tool -> 知识库查询 tool")
    upload = st.file_uploader("上传中草药图片（通过文件名关键词做模拟识别）", type=["png", "jpg", "jpeg", "webp"])
    if upload is not None:
        st.image(upload, caption=f"上传文件: {upload.name}", use_container_width=True)
        agent_result = run_herb_agent(upload.name)
        result = agent_result["identify_result"]
        if result["status"] == "ok":
            top1 = result["top1"]
            st.success(f"识别结果: {top1['name']} (置信度 {top1['confidence']:.2f})")
            st.markdown("候选结果:")
            st.json(result["candidates"])
        else:
            st.error("当前无法可靠识别，系统已自动提交错误信息至药剂师人工复核队列。")
            auto_report = create_error_report(
                trigger="auto_unrecognized",
                user=current_user,
                image_name=upload.name,
                note="系统自动上报：识别置信度不足或未知药材。",
                model_candidates=result["candidates"],
            )
            st.caption(f"自动报错单号：{auto_report['report_id']}")
            st.json(result["candidates"])

        with st.expander("查看智能体工具调用轨迹"):
            st.json(agent_result["tool_calls"])

        st.markdown("### 识别结果报错（人工复核申请）")
        report_reason = st.text_area(
            "若识别有误，请填写报错说明并提交给药剂师",
            key=f"report_reason_{upload.name}",
            placeholder="例如：识别成甘草，但我判断是黄芪。",
        )
        if st.button("提交识别报错", key=f"submit_report_{upload.name}"):
            if not report_reason.strip():
                st.warning("请先填写报错说明。")
            else:
                report_entry = create_error_report(
                    trigger="user_reported_error",
                    user=current_user,
                    image_name=upload.name,
                    note=report_reason.strip(),
                    model_candidates=result["candidates"],
                )
                st.success(f"报错已提交，药剂师将进行人工复核。单号：{report_entry['report_id']}")

    if not current_user or current_user["role"] != "pharmacist":
        st.info("药方审核、人工复核处理台、统计看板仅对药师开放。")
    else:
        st.divider()
        st.subheader("中草药药方审核（药师权限）")
        st.caption("支持两种输入：1) 处方二维码文本；2) 手工录入药方")
        rx_qr_text = st.text_input(
            "处方二维码（演示用文本）",
            placeholder="RX|id=RX20260413-001|items=甘草6g,海藻10g,当归12g",
            key="rx_qr_input",
        )
        rx_input = st.text_area("手工输入药方（药名+剂量，逗号或换行分隔）", height=120, placeholder="甘草6g，海藻10g，当归12g")

        if st.button("执行药方复核并登记调配人", type="primary"):
            rx_id = f"RX-{uuid4().hex[:8]}"
            rx_source = "manual_input"
            final_rx_text = rx_input.strip()
            if rx_qr_text.strip():
                qr_payload, qr_error = parse_prescription_qr(rx_qr_text)
                if qr_error:
                    st.error(qr_error)
                    st.stop()
                rx_id = qr_payload["rx_id"]
                final_rx_text = qr_payload["items_text"]
                rx_source = "prescription_qr"

            if not final_rx_text:
                st.error("未获取到处方药材内容，请输入药方或提供合法处方二维码。")
                st.stop()

            herbs = parse_prescription(final_rx_text)
            if not herbs:
                st.error("未识别到任何药名，请检查输入格式。")
            else:
                issues = review_prescription(herbs)
                st.write("处方编号：", rx_id)
                st.write("解析到的药材：", herbs)
                for issue in issues:
                    if issue["level"] == "error":
                        st.error(issue["detail"])
                    elif issue["level"] == "warn":
                        st.warning(issue["detail"])
                    else:
                        st.info(issue["detail"])

                logs = load_dispense_logs()
                logs.append(
                    {
                        "timestamp": datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
                        "prescription_id": rx_id,
                        "prescription_source": rx_source,
                        "pharmacist_id": current_user["id"],
                        "pharmacist_name": current_user["name"],
                        "herbs": herbs,
                        "issues": issues,
                    }
                )
                save_dispense_logs(logs)
                st.success("药方复核已登记。")

        st.divider()
        st.subheader("药剂师人工复核处理台")
        all_reports = load_error_reports()
        pending = [x for x in all_reports if x.get("status") == "pending"]
        resolved = [x for x in all_reports if x.get("status") == "resolved"]
        st.caption(f"待处理报错：{len(pending)} 条；已处理：{len(resolved)} 条")

        if pending:
            selected_report_id = st.selectbox(
                "选择待处理报错单",
                options=[r["report_id"] for r in pending],
                key="pending_report_select",
            )
            selected_report = next((r for r in pending if r["report_id"] == selected_report_id), None)
            st.json(selected_report)
            corrected_herb = st.text_input("人工复核后的正确药材名称", key="corrected_herb_input", placeholder="例如：黄芪")
            if st.button("提交人工复核结果", key="resolve_report_btn"):
                if not corrected_herb.strip():
                    st.warning("请先填写人工确认的药材名称。")
                else:
                    updated = load_error_reports()
                    for item in updated:
                        if item["report_id"] == selected_report_id:
                            item["status"] = "resolved"
                            item["corrected_herb"] = corrected_herb.strip()
                            item["resolved_by"] = f"{current_user['name']}({current_user['id']})"
                            item["resolved_at"] = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
                            break
                    save_error_reports(updated)
                    st.success("人工复核已完成并回写。")
                    st.rerun()
        else:
            st.caption("当前没有待处理报错单。")

        st.divider()
        st.subheader("药师统计看板")
        all_logs = load_dispense_logs()
        stats = count_by_pharmacist(all_logs)
        if not stats:
            st.caption("暂无药方复核统计数据。")
        else:
            summary_rows = [{"药剂师": k, "处方数": v} for k, v in sorted(stats.items(), key=lambda x: x[1], reverse=True)]
            st.table(summary_rows)
            st.caption(f"累计药方复核记录：{len(all_logs)}")

    st.divider()
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


def render_login_page():
    st.title("登录页面")
    st.caption("系统角色仅支持：一般用户、药师")
    if st.button("返回主页", key="back_home"):
        st.session_state.page = "home"
        st.rerun()

    role = st.radio("选择登录角色", options=["一般用户", "药师"], horizontal=True)
    if role == "一般用户":
        user_name = st.text_input("用户姓名", placeholder="例如：李四", key="normal_user_name")
        if st.button("以一般用户身份登录", type="primary", key="normal_login"):
            name = user_name.strip() or "一般用户"
            st.session_state.user = {"id": f"U-{uuid4().hex[:6]}", "name": name, "role": "general"}
            st.session_state.page = "home"
            st.success(f"登录成功：{name}（一般用户）")
            st.rerun()
    else:
        pharm_qr_text = st.text_input(
            "请扫描药师二维码（演示用文本）",
            placeholder="PHARM|id=P001|name=张三",
            key="pharm_qr_input",
        )
        if st.button("以药师身份登录", type="primary", key="pharm_login"):
            pharmacist, error = parse_pharmacist_qr(pharm_qr_text)
            if error:
                st.error(error)
            else:
                st.session_state.user = {"id": pharmacist["id"], "name": pharmacist["name"], "role": "pharmacist"}
                st.session_state.page = "home"
                st.success(f"登录成功：{pharmacist['name']}（药师）")
                st.rerun()


if st.session_state.page == "login":
    render_login_page()
else:
    render_home_page()
