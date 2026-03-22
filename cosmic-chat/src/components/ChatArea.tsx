import { useRef, useEffect, useState } from "react";
import { Sparkles } from "lucide-react";
import MessageBubble, { type Message } from "./MessageBubble";
import ChatInput from "./ChatInput";
import { api } from "@/lib/api";

interface ChatAreaProps {
  messages: Message[];
  onSend: (text: string) => void;
  sessionId: string | null;
  onMessagesUpdate: (messages: Message[]) => void;
  onUploadSuccess?: () => void;
  onSessionCreated?: (sessionId: string) => void;
}

const WelcomeScreen = () => (
  <div className="flex-1 flex flex-col items-center justify-center gap-4 animate-fade-in">
    <div className="w-16 h-16 rounded-full bg-gradient-to-br from-primary to-accent flex items-center justify-center animate-pulse-glow">
      <Sparkles className="w-8 h-8 text-primary-foreground" />
    </div>
    <h1 className="text-2xl font-semibold text-foreground">今天有什么可以帮到你？</h1>
    <p className="text-sm text-muted-foreground max-w-md text-center">
      在星空下与AI对话，探索无限可能。问我任何问题，我会尽力帮助你。
    </p>
    <div className="grid grid-cols-2 gap-3 mt-4 max-w-lg">
      {[
        "帮我优化一段Python代码",
        "解释量子计算的基本原理",
        "写一首关于星空的诗",
        "设计一个REST API方案",
      ].map((suggestion) => (
        <button
          key={suggestion}
          className="text-left px-4 py-3 rounded-xl border border-border bg-muted/30 text-sm text-muted-foreground hover-gradient transition-all duration-200"
        >
          {suggestion}
        </button>
      ))}
    </div>
  </div>
);

const ChatArea = ({ messages, onSend, sessionId, onMessagesUpdate, onUploadSuccess, onSessionCreated }: ChatAreaProps) => {
  const scrollRef = useRef<HTMLDivElement>(null);
  const [isLoading, setIsLoading] = useState(false);

  useEffect(() => {
    if (scrollRef.current) {
      scrollRef.current.scrollTop = scrollRef.current.scrollHeight;
    }
  }, [messages]);

  const handleSend = async (text: string) => {
    if (!text.trim() || isLoading) return;

    const userMsg: Message = {
      id: Date.now(),
      role: 'user',
      content: text,
      time: new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' }),
    };

    onMessagesUpdate([...messages, userMsg]);
    setIsLoading(true);

    try {
      const currentSessionId = sessionId || '';
      const response = await api.conversations.chat(currentSessionId, text);
      const aiResponse = response.answer;

      const aiMsg: Message = {
        id: Date.now() + 1,
        role: 'ai',
        content: aiResponse,
        time: new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' }),
      };

      onMessagesUpdate([...messages, userMsg, aiMsg]);

      if (!sessionId && response.sessionId) {
        onSessionCreated?.(response.sessionId);
      }
    } catch (error) {
      console.error('[发送消息失败]', error);
      const errorMsg: Message = {
        id: Date.now() + 1,
        role: 'ai',
        content: '抱歉，发送消息时出现错误，请稍后重试。',
        time: new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' }),
      };
      onMessagesUpdate([...messages, userMsg, errorMsg]);
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div className="flex-1 flex flex-col h-screen bg-background starfield" style={{ backgroundImage: "url('/bg.png')" }}>
      {messages.length === 0 ? (
        <>
          <WelcomeScreen />
          <ChatInput onSend={handleSend} disabled={isLoading} onUploadSuccess={onUploadSuccess} />
        </>
      ) : (
        <>
          <div ref={scrollRef} className="flex-1 overflow-y-auto px-4 py-6">
            <div className="max-w-3xl mx-auto space-y-6">
              {messages.map((msg) => (
                <MessageBubble key={msg.id} message={msg} />
              ))}
              {isLoading && (
                <div className="flex items-center gap-2 text-muted-foreground">
                  <div className="w-2 h-2 rounded-full bg-primary animate-pulse"></div>
                  <div className="w-2 h-2 rounded-full bg-primary animate-pulse" style={{ animationDelay: '0.2s' }}></div>
                  <div className="w-2 h-2 rounded-full bg-primary animate-pulse" style={{ animationDelay: '0.4s' }}></div>
                </div>
              )}
            </div>
          </div>
          <ChatInput onSend={handleSend} disabled={isLoading} onUploadSuccess={onUploadSuccess} />
        </>
      )}
    </div>
  );
};

export default ChatArea;
