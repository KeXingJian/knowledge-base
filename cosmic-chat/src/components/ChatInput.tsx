import { useState, useRef, useEffect } from "react";
import { Send, Paperclip, Upload, X, CheckCircle, AlertCircle } from "lucide-react";
import { api } from "@/lib/api";

interface ChatInputProps {
  onSend: (message: string) => void;
  disabled?: boolean;
  onUploadSuccess?: () => void;
}

const ChatInput = ({ onSend, disabled = false, onUploadSuccess }: ChatInputProps) => {
  const [text, setText] = useState("");
  const [uploading, setUploading] = useState(false);
  const [uploadProgress, setUploadProgress] = useState(0);
  const [uploadStatus, setUploadStatus] = useState<'idle' | 'uploading' | 'success' | 'error'>('idle');
  const [uploadMessage, setUploadMessage] = useState("");
  const fileInputRef = useRef<HTMLInputElement>(null);
  const pollIntervalRef = useRef<NodeJS.Timeout | null>(null);

  useEffect(() => {
    return () => {
      if (pollIntervalRef.current) {
        clearInterval(pollIntervalRef.current);
      }
    };
  }, []);

  const handleSend = () => {
    if (!text.trim() || disabled) return;
    onSend(text.trim());
    setText("");
  };

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === "Enter" && !e.shiftKey) {
      e.preventDefault();
      handleSend();
    }
  };

  const handleFileSelect = () => {
    fileInputRef.current?.click();
  };

  const handleFileChange = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const files = e.target.files;
    if (!files || files.length === 0) return;

    const fileArray = Array.from(files);

    try {
      setUploading(true);
      setUploadStatus('uploading');
      setUploadProgress(0);
      setUploadMessage(`正在上传 ${fileArray.length} 个文件...`);
      console.log('[开始上传文件]', fileArray.map(f => f.name));

      const taskId = await api.documents.batchUpload(fileArray);
      console.log('[上传任务ID]', taskId);

      if (!taskId) {
        console.error('[上传返回的任务ID为空]', taskId);
        setUploadStatus('error');
        setUploadMessage('上传失败：未获取到任务ID');
        setUploading(false);
        setTimeout(() => {
          setUploadStatus('idle');
          setUploadMessage('');
        }, 3000);
        return;
      }

      setUploadMessage('正在处理文档...');
      setUploadProgress(0);

      const pollProgress = async () => {
        try {
          const currentProgress = await api.documents.getUploadProgress(taskId);
          console.log('[上传进度]', currentProgress);

          setUploadProgress(currentProgress.progress);
          setUploadMessage(`正在处理文档... (${Math.round(currentProgress.progress)}%)`);

          if (currentProgress.status === 'COMPLETED') {
            setUploadMessage('文档处理完成！');
            setUploadProgress(100);
            setUploadStatus('success');
            setUploading(false);

            if (pollIntervalRef.current) {
              clearInterval(pollIntervalRef.current);
              pollIntervalRef.current = null;
            }

            setTimeout(() => {
              setUploadStatus('idle');
              setUploadMessage('');
              onUploadSuccess?.();
            }, 2000);
          } else if (currentProgress.status === 'FAILED') {
            setUploadStatus('error');
            setUploadMessage('文档处理失败');
            setUploading(false);

            if (pollIntervalRef.current) {
              clearInterval(pollIntervalRef.current);
              pollIntervalRef.current = null;
            }

            setTimeout(() => {
              setUploadStatus('idle');
              setUploadMessage('');
            }, 3000);
          }
        } catch (error) {
          console.error('[获取上传进度失败]', error);
        }
      };

      pollIntervalRef.current = setInterval(pollProgress, 2000);
      pollProgress();

    } catch (error) {
      console.error('[文件上传失败]', error);
      setUploadStatus('error');
      setUploadMessage('文件上传失败，请重试');

      if (pollIntervalRef.current) {
        clearInterval(pollIntervalRef.current);
        pollIntervalRef.current = null;
      }

      setUploading(false);

      setTimeout(() => {
        setUploadStatus('idle');
        setUploadMessage('');
      }, 3000);
    } finally {
      if (fileInputRef.current) {
        fileInputRef.current.value = '';
      }
    }
  };

  const handleDismissUpload = () => {
    setUploadStatus('idle');
    setUploadMessage('');
  };

  return (
    <div className="p-4 border-t border-border">
      <div className="max-w-3xl mx-auto">
        {uploadStatus !== 'idle' && (
          <div className={`mb-3 p-3 rounded-lg flex items-center gap-3 ${uploadStatus === 'error'
            ? 'bg-destructive/10 text-destructive'
            : 'bg-primary/10 text-primary'
            }`}>
            {uploadStatus === 'uploading' && (
              <Upload className="w-4 h-4 animate-pulse" />
            )}
            {uploadStatus === 'success' && (
              <CheckCircle className="w-4 h-4" />
            )}
            {uploadStatus === 'error' && (
              <AlertCircle className="w-4 h-4" />
            )}
            <span className="text-sm flex-1">{uploadMessage}</span>
            <button
              onClick={handleDismissUpload}
              className="p-1 hover:bg-black/10 rounded transition-colors"
            >
              <X className="w-4 h-4" />
            </button>
          </div>
        )}
        <div className="flex items-end gap-2 bg-muted rounded-[20px] px-4 py-2 input-glow transition-all duration-300 border border-border focus-within:border-primary/50">
          <input
            type="file"
            ref={fileInputRef}
            onChange={handleFileChange}
            multiple
            accept=".txt,.pdf,.doc,.docx"
            className="hidden"
          />
          <button
            onClick={handleFileSelect}
            className="p-1.5 text-muted-foreground hover:text-foreground transition-colors shrink-0 mb-0.5"
            disabled={disabled || uploading}
          >
            <Paperclip className="w-4 h-4" />
          </button>
          <textarea
            value={text}
            onChange={(e) => setText(e.target.value)}
            onKeyDown={handleKeyDown}
            placeholder="给 StarChat 发送消息..."
            rows={1}
            disabled={disabled || uploading}
            className="flex-1 bg-transparent resize-none text-sm text-foreground placeholder:text-muted-foreground focus:outline-none py-1.5 max-h-32 disabled:opacity-50 disabled:cursor-not-allowed"
            style={{ minHeight: "24px" }}
          />
          <button
            onClick={handleSend}
            disabled={!text.trim() || disabled || uploading}
            className="p-2 rounded-full bg-primary text-primary-foreground btn-glow transition-all duration-300 disabled:opacity-30 disabled:shadow-none disabled:cursor-not-allowed shrink-0 mb-0.5 hover:brightness-110"
          >
            <Send className="w-4 h-4" />
          </button>
        </div>
        <p className="text-center text-[10px] text-muted-foreground mt-2">
          StarChat AI 可能会产生不准确的信息，请核实重要内容。
        </p>
      </div>
    </div>
  );
};

export default ChatInput;
