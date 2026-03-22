import { useState, useEffect } from "react";
import { X, FileText } from "lucide-react";
import { type Document } from "@/lib/api";
import { api } from "@/lib/api";

interface DocumentViewerProps {
  document: Document | null;
  onClose: () => void;
}

const DocumentViewer = ({ document, onClose }: DocumentViewerProps) => {
  const [content, setContent] = useState<string>("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (document && document.fileType === 'txt') {
      loadDocumentContent();
    }
  }, [document]);

  const loadDocumentContent = async () => {
    if (!document) return;

    try {
      setLoading(true);
      setError(null);

      console.log('[开始加载文档]', { fileName: document.fileName, fileType: document.fileType, id: document.id });

      const minioUrl = await api.documents.getDetail(document.id);
      console.log('[获取到MinIO URL]', minioUrl);

      const response = await fetch(minioUrl);
      if (!response.ok) {
        throw new Error(`加载文档失败: ${response.status} ${response.statusText}`);
      }

      const text = await response.text();
      console.log('[文档内容长度]', text.length);
      console.log('[文档内容前100字符]', text.substring(0, 100));

      if (text.includes('<!doctype html>') || text.includes('<html')) {
        console.error('[文档内容是HTML]', text.substring(0, 200));
        throw new Error('文档地址返回了HTML页面，可能是MinIO配置问题或文件不存在');
      }

      setContent(text);
      console.log('[加载文档内容成功]', document.fileName);
    } catch (err) {
      console.error('[加载文档内容失败]', err);
      setError(`加载文档内容失败: ${err instanceof Error ? err.message : '未知错误'}`);
    } finally {
      setLoading(false);
    }
  };

  if (!document) {
    return null;
  }

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
          <button
            onClick={onClose}
            className="p-2 rounded-lg hover:bg-secondary transition-colors text-muted-foreground hover:text-foreground"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Content */}
        <div className="flex-1 overflow-y-auto p-6">
          {loading ? (
            <div className="flex items-center justify-center h-full">
              <div className="flex items-center gap-2 text-muted-foreground">
                <div className="w-2 h-2 rounded-full bg-primary animate-pulse"></div>
                <div className="w-2 h-2 rounded-full bg-primary animate-pulse" style={{ animationDelay: '0.2s' }}></div>
                <div className="w-2 h-2 rounded-full bg-primary animate-pulse" style={{ animationDelay: '0.4s' }}></div>
              </div>
            </div>
          ) : error ? (
            <div className="flex items-center justify-center h-full">
              <p className="text-destructive">{error}</p>
            </div>
          ) : document.fileType === 'txt' ? (
            <pre className="whitespace-pre-wrap text-sm text-foreground leading-relaxed font-mono">
              {content}
            </pre>
          ) : (
            <div className="flex items-center justify-center h-full">
              <p className="text-muted-foreground">暂不支持查看此文件类型</p>
            </div>
          )}
        </div>

        {/* Footer */}
        <div className="p-4 border-t border-border bg-muted/30">
          <div className="flex items-center justify-between text-xs text-muted-foreground">
            <span>上传时间: {new Date(document.uploadTime).toLocaleString('zh-CN')}</span>
            <span>分片数量: {document.chunkCount}</span>
          </div>
        </div>
      </div>
    </div>
  );
};

export default DocumentViewer;
