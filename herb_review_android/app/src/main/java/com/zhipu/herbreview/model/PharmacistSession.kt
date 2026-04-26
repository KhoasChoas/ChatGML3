package com.zhipu.herbreview.model

data class PharmacistSession(
    val employeeId: String,
    val displayName: String,
    val isDepartmentDirector: Boolean,
    val canSubmitErrorReport: Boolean = true,
)
