package com.zhipu.herbreview.ui.integration

/**
 * 单条集成步骤在界面上的结果，用于「是否成功」的可视化。
 */
sealed class IntegrationOutcome {
    /** 与当前模式无关，不展示该行 */
    data object Hidden : IntegrationOutcome()

    /** 尚未执行（例如会话创建前要先进第 1 步） */
    data object Waiting : IntegrationOutcome()

    /** 请求进行中 */
    data object Working : IntegrationOutcome()

    /** 本流程未使用（如未配置 API 时的远程步骤） */
    data object NotApplicable : IntegrationOutcome()

    data class Ok(val detail: String) : IntegrationOutcome()
    data class Fail(val message: String) : IntegrationOutcome()
}
