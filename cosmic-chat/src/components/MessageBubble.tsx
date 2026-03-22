import { Sparkles, User } from "lucide-react";

export interface Message {
  id: string;
  role: "ai" | "user";
  content: string;
  time: string;
}

const MessageBubble = ({ message }: { message: Message }) => {
  const isAI = message.role === "ai";

  return (
    <div
      className={`flex gap-3 animate-fade-in ${isAI ? "justify-start" : "justify-end"}`}
    >
      {isAI && (
        <div className="w-8 h-8 rounded-full bg-gradient-to-br from-primary to-accent flex items-center justify-center shrink-0 mt-1">
          <Sparkles className="w-4 h-4 text-primary-foreground" />
        </div>
      )}

      <div className={`max-w-[70%] space-y-1 ${isAI ? "" : "flex flex-col items-end"}`}>
        <div
          className={`px-4 py-3 rounded-bubble text-sm leading-relaxed ${
            isAI
              ? "bg-gradient-to-br from-secondary to-muted text-foreground"
              : "bg-accent text-accent-foreground"
          }`}
        >
          {message.content.split("\n").map((line, i) => {
            if (line.startsWith("```")) {
              return null;
            }
            if (line.startsWith("`") && line.endsWith("`")) {
              return (
                <code key={i} className="bg-background/50 px-1.5 py-0.5 rounded text-xs font-mono text-primary">
                  {line.slice(1, -1)}
                </code>
              );
            }
            return <p key={i}>{line || <br />}</p>;
          })}
        </div>
        <span className="text-[10px] text-muted-foreground px-1">{message.time}</span>
      </div>

      {!isAI && (
        <div className="w-8 h-8 rounded-full bg-accent/60 flex items-center justify-center shrink-0 mt-1">
          <User className="w-4 h-4 text-accent-foreground" />
        </div>
      )}
    </div>
  );
};

export default MessageBubble;
