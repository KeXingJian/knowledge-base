import { useState, useEffect } from "react";
import { X, FileText, Download } from "lucide-react";
import { type Document } from "@/lib/api";
import { api } from "@/lib/api";

interface DocumentViewerProps {
  document: Document | null;
  onClose: () => void;
}

const TEXT_TYPES = new Set(["txt", "md", "csv", "log", "json", "xml", "yaml", "yml"]);
const IMAGE_TYPES = new Set(["png", "jpg", "jpeg", "gif", "webp", "svg"]);
const PDF_TYPES = new Set(["pdf"]);

const DocumentViewer = ({ document, onClose }: DocumentViewerProps) => {
  const [textContent, setTextContent] = useState<string>("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const fileType = document?.fileType?.toLowerCase() ?? "";
  const contentUrl = document ? api.documents.getContentUrl(document.id) : "";
  const downloadUrl = document ? api.documents.getContentUrl(document.id, true) : "";

  useEffect(() => {
    if (!document) return;
    setTextContent("");
    setError(null);

    if (TEXT_TYPES.has(fileType)) {
      setLoading(true);
      fetch(contentUrl)
        .then(res => {
          if (!res.ok) throw new Error(`HTTP ${res.status}`);
          return res.text();
        })
        .then(setTextContent)
        .catch(err => setError(`加载失败: ${err.message}`))
        .finally(() => setLoading(false));
    }
  }, [document]);

  if (!document) return null;

  const renderContent = () => {
    if (loading) {
      return (
        <div className="flex items-center justify-center h-full">
          <div className="flex items-center gap-2 text-muted-foreground">
            {[0, 0.2, 0.4].map((delay, i) => (
              <div
                key={i}
                className="w-2 h-2 rounded-full bg-primary animate-pulse"
                style={{ animationDelay: `${delay}s` }}
              />
            ))}
          </div>
        </div>
      );
    }

    if (error) {
      return (
        <div className="flex items-center justify-center h-full">
          <p className="text-destructive">{error}</p>
        </div>
      );
    }

    if (TEXT_TYPES.has(fileType)) {
      return (
        <pre className="whitespace-pre-wrap text-sm text-foreground leading-relaxed font-mono">
          {textContent}
        </pre>
      );
    }

    if (PDF_TYPES.has(fileType)) {
      return (
        <iframe
          src={contentUrl}
          className="w-full h-full rounded-lg border-0"
          title={document.fileName}
        />
      );
    }

    if (IMAGE_TYPES.has(fileType)) {
      return (
        <div className="flex items-center justify-center h-full">
          <img
            src={contentUrl}
            alt={document.fileName}
            className="max-w-full max-h-full object-contain rounded-lg"
          />
        </div>
      );
    }

    return (
      <div className="flex flex-col items-center justify-center h-full gap-4">
        <FileText className="w-16 h-16 text-muted-foreground/40" />
        <p className="text-muted-foreground">暂不支持在线预览此文件类型</p>
        <a
          href={downloadUrl}
          className="flex items-center gap-2 px-4 py-2 rounded-lg bg-primary text-primary-foreground text-sm font-medium hover:bg-primary/90 transition-colors"
        >
          <Download className="w-4 h-4" />
          下载文件
        </a>
      </div>
    );
  };

  return (
    <div className="fixed inset-0 bg-background/80 backdrop-blur-sm z-50 flex items-center justify-center p-4">
      <div className="bg-card border border-border rounded-2xl shadow-2xl max-w-4xl w-full max-h-[80vh] flex flex-col">
        {/* Header */}
        <div className="flex items-center justify-between p-4 border-b border-border">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-lg bg-primary/10 flex items-center justify-center">
              <FileText className="w-5 h-5 text-primary" />
            </div>
            <div>
              <h2 className="text-lg font-semibold text-foreground">{document.fileName}</h2>
              <p className="text-xs text-muted-foreground">
                {document.fileType.toUpperCase()} · {(document.fileSize / 1024).toFixed(1)} KB
              </p>
            </div>
          </div>
          <div className="flex items-center gap-2">
            <a
              href={downloadUrl}
              title="下载"
              className="p-2 rounded-lg hover:bg-secondary transition-colors text-muted-foreground hover:text-foreground"
            >
              <Download className="w-5 h-5" />
            </a>
            <button
              onClick={onClose}
              className="p-2 rounded-lg hover:bg-secondary transition-colors text-muted-foreground hover:text-foreground"
            >
              <X className="w-5 h-5" />
            </button>
          </div>
        </div>

        {/* Content */}
        <div className="flex-1 overflow-auto p-6">
          {renderContent()}
        </div>

        {/* Footer */}
        <div className="p-4 border-t border-border bg-muted/30">
          <div className="flex items-center justify-between text-xs text-muted-foreground">
            <span>上传时间: {new Date(document.uploadTime).toLocaleString("zh-CN")}</span>
            <span>分片数量: {document.chunkCount}</span>
          </div>
        </div>
      </div>
    </div>
  );
};

export default DocumentViewer;
