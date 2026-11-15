package com.jij6303.sidecoach.feature

/**
 * 주행 세션 저장 대상. 프로세스가 다시 만들어져도 이어가야 하는 상태를 [S]로 내보내고 되살린다.
 * 저장소(app의 SessionStore)는 이 상태들을 묶어 저장할 뿐 내용은 모른다.
 */
interface Restorable<S> {
    fun exportState(): S

    /** [nowMs]는 지금 시각(스냅샷과 같은 시계). 내려가 있던 시간을 빼야 하는 객체만 쓴다. */
    fun restore(state: S, nowMs: Long)
}
