package com.example.autoglmagent.agent

object ActionParser {
    fun splitResponse(content: String): ModelResponse {
        val raw = content.trim()
        val finishIndex = raw.indexOf("finish(message=")
        if (finishIndex >= 0) {
            return ModelResponse(
                thinking = raw.substring(0, finishIndex).stripThinkTags(),
                actionText = raw.substring(finishIndex).stripAnswerTags(),
                rawContent = content,
            )
        }

        val doIndex = raw.indexOf("do(action=")
        if (doIndex >= 0) {
            return ModelResponse(
                thinking = raw.substring(0, doIndex).stripThinkTags(),
                actionText = raw.substring(doIndex).stripAnswerTags(),
                rawContent = content,
            )
        }

        val answerStart = raw.indexOf("<answer>")
        if (answerStart >= 0) {
            val thinking = raw.substring(0, answerStart).stripThinkTags()
            val action = raw.substring(answerStart + "<answer>".length).stripAnswerTags()
            return ModelResponse(thinking, action, content)
        }

        return ModelResponse(thinking = "", actionText = raw, rawContent = content)
    }

    fun parse(raw: String): AgentAction {
        val s = raw.trim().stripAnswerTags()
        parseFinish(s)?.let { return it }

        val actionName = findStringArg(s, "action") ?: return AgentAction.Unknown(s)
        return when (actionName) {
            "Launch" -> AgentAction.Launch(findStringArg(s, "app").orEmpty())
            "Tap" -> {
                val point = findPointArg(s, "element") ?: return AgentAction.Unknown(s)
                AgentAction.Tap(point.first, point.second, findStringArg(s, "message"))
            }
            "Type", "Type_Name" -> AgentAction.TypeText(findStringArg(s, "text").orEmpty())
            "Swipe" -> {
                val start = findPointArg(s, "start") ?: return AgentAction.Unknown(s)
                val end = findPointArg(s, "end") ?: return AgentAction.Unknown(s)
                AgentAction.Swipe(start.first, start.second, end.first, end.second)
            }
            "Long Press" -> {
                val point = findPointArg(s, "element") ?: return AgentAction.Unknown(s)
                AgentAction.LongPress(point.first, point.second)
            }
            "Double Tap" -> {
                val point = findPointArg(s, "element") ?: return AgentAction.Unknown(s)
                AgentAction.DoubleTap(point.first, point.second)
            }
            "Wait" -> AgentAction.Wait(findDuration(s))
            "Back" -> AgentAction.Back
            "Home" -> AgentAction.Home
            "Take_over" -> AgentAction.TakeOver(findStringArg(s, "message") ?: "需要人工接管")
            "Note", "Call_API", "Interact" -> AgentAction.Unknown(s)
            else -> AgentAction.Unknown(s)
        }
    }

    private fun parseFinish(s: String): AgentAction.Finish? {
        val start = s.indexOf("finish(message=")
        if (start < 0) return null
        return AgentAction.Finish(findStringArg(s, "message") ?: s.substringAfter("finish(message=").trim(' ', ')', '"', '\''))
    }

    private fun findDuration(s: String): Double {
        val value = findStringArg(s, "duration") ?: return 1.0
        return Regex("""\d+(?:\.\d+)?""").find(value)?.value?.toDoubleOrNull() ?: 1.0
    }

    private fun findPointArg(s: String, key: String): Pair<Int, Int>? {
        val match = Regex("""$key\s*=\s*\[\s*(\d{1,4})\s*,\s*(\d{1,4})\s*]""").find(s) ?: return null
        return match.groupValues[1].toInt() to match.groupValues[2].toInt()
    }

    private fun findStringArg(s: String, key: String): String? {
        val start = s.indexOf("$key=")
        if (start < 0) return null
        var i = start + key.length + 1
        while (i < s.length && s[i].isWhitespace()) i++
        if (i >= s.length) return null

        val quote = s[i]
        if (quote == '"' || quote == '\'') {
            val builder = StringBuilder()
            i++
            var escaped = false
            while (i < s.length) {
                val ch = s[i]
                if (escaped) {
                    builder.append(
                        when (ch) {
                            'n' -> '\n'
                            'r' -> '\r'
                            't' -> '\t'
                            else -> ch
                        }
                    )
                    escaped = false
                } else if (ch == '\\') {
                    escaped = true
                } else if (ch == quote) {
                    return builder.toString()
                } else {
                    builder.append(ch)
                }
                i++
            }
            return builder.toString()
        }

        val end = s.indexOfAny(charArrayOf(',', ')'), i).let { if (it < 0) s.length else it }
        return s.substring(i, end).trim()
    }

    private fun String.stripThinkTags(): String =
        replace("<think>", "")
            .replace("</think>", "")
            .replace("<answer>", "")
            .replace("</answer>", "")
            .trim()

    private fun String.stripAnswerTags(): String =
        replace("<answer>", "")
            .replace("</answer>", "")
            .trim()
}
