package com.forA.chatbot.chat.domain.enums;

public enum MessageType
{
    TEXT, // 일반 텍스트 응답 (GPT AI와 대화할 경우)
    OPTION,
    INPUT // 키보드 입력형 응답 (예: 생년 입력, 현재 상황 입력)
}