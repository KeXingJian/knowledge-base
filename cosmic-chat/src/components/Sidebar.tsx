import { useState, useEffect, forwardRef, useImperativeHandle, useMemo, useRef, useCallback } from "react";
import { Plus, MessageSquare, Settings, FileText, Trash2 } from "lucide-react";
import { api, type Conversation, type Document } from "@/lib/api";

interface ChatHistory {
  id: string;
  title: string;
  time: string;
  group: string;
}

interface SidebarProps {
  activeChat: string;
  onSelectChat: (id: string) => void;
  onNewChat: () => void;
  onSelectDocument?: (document: Document) => void;
}

const Sidebar = forwardRef<{ refreshDocuments: () => void }, SidebarProps>(({ activeChat, onSelectChat, onNewChat, onSelectDocument }, ref) => {
  const [view, setView] = useState<'chats' | 'documents'>('chats');
  const [conversations, setConversations] = useState<Conversation[]>([]);
  const [documents, setDocuments] = useState<Document[]>([]);
  const [loading, setLoading] = useState(false);
  const [documentsLoaded, setDocumentsLoaded] = useState(false);
  const [visibleDocs, setVisibleDocs] = useState<Document[]>([]);
  const [startIndex, setStartIndex] = useState(0);

  const scrollContainerRef = useRef<HTMLDivElement>(null);
  const ITEM_HEIGHT = 60;

  useImperativeHandle(ref, () => ({
    refreshDocuments: async () => {
      try {
        const data = await api.documents.getList();
        setDocuments(data);
        console.log('[刷新文档列表成功]', data.length);
      } catch (error) {
        console.error('[刷新文档列表失败]', error);
      }
    },
  }));

  useEffect(() => {
    loadConversations();
  }, []);

  useEffect(() => {
    if (view === 'documents' && !documentsLoaded) {
      loadDocuments();
    }
  }, [view, documentsLoaded]);

  const loadConversations = async () => {
    try {
      setLoading(true);
      const data = await api.conversations.getAll();
      setConversations(data);
      console.log('[加载对话列表成功]', data.length);
    } catch (error) {
      console.error('[加载对话列表失败]', error);
    } finally {
      setLoading(false);
    }
  };

  const loadDocuments = async () => {
    try {
      setLoading(true);
      const data = await api.documents.getList();
      setDocuments(data);
      setDocumentsLoaded(true);
      console.log('[加载文档列表成功]', data.length);
    } catch (error) {
      console.error('[加载文档列表失败]', error);
    } finally {
      setLoading(false);
    }
  };

  const handleDeleteDocument = async (documentId: number, e: React.MouseEvent) => {
    e.stopPropagation();
    if (!confirm('确定要删除此文档吗？')) return;

    try {
      await api.documents.delete(documentId);
      setDocuments(documents.filter(doc => doc.id !== documentId));
      console.log('[删除文档成功]', documentId);
    } catch (error) {
      console.error('[删除文档失败]', error);
      alert('删除文档失败，请稍后重试');
    }
  };

  const handleDeleteConversation = async (sessionId: string, e: React.MouseEvent) => {
    e.stopPropagation();
    if (!confirm('确定要删除此对话吗？')) return;

    try {
      await api.conversations.delete(sessionId);
      setConversations(conversations.filter(conv => conv.sessionId !== sessionId));
      if (activeChat === sessionId) {
        onNewChat();
      }
      console.log('[删除对话成功]', sessionId);
    } catch (error) {
      console.error('[删除对话失败]', error);
      alert('删除对话失败，请稍后重试');
    }
  };

  const formatFileSize = (bytes: number) => {
    if (bytes < 1024) return bytes + ' B';
    if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB';
    return (bytes / (1024 * 1024)).toFixed(1) + ' MB';
  };

  const formatTime = (dateString: string) => {
    const date = new Date(dateString);
    const now = new Date();
    const diff = now.getTime() - date.getTime();
    const minutes = Math.floor(diff / 60000);
    const hours = Math.floor(diff / 3600000);
    const days = Math.floor(diff / 86400000);

    if (minutes < 1) return '刚刚';
    if (minutes < 60) return `${minutes}分钟前`;
    if (hours < 24) return `${hours}小时前`;
    if (days < 7) return `${days}天前`;
    return date.toLocaleDateString('zh-CN');
  };

  const groupConversations = (convs: Conversation[]) => {
    if (!convs || convs.length === 0) return {};
    
    const groups: Record<string, Conversation[]> = {};
    convs.forEach(conv => {
      const date = new Date(conv.createTime);
      const now = new Date();
      const diff = now.getTime() - date.getTime();
      const days = Math.floor(diff / 86400000);

      let group = '更早';
      if (days === 0) group = '今天';
      else if (days === 1) group = '昨天';
      else if (days < 7) group = '7天内';

      if (!groups[group]) groups[group] = [];
      groups[group].push(conv);
    });
    return groups;
  };

  const conversationGroups = useMemo(() => groupConversations(conversations), [conversations]);

  const handleScroll = useCallback(() => {
    if (!scrollContainerRef.current || view !== 'documents') return;

    const scrollTop = scrollContainerRef.current.scrollTop;
    const containerHeight = scrollContainerRef.current.clientHeight;
    const endIndex = Math.min(
      Math.ceil((scrollTop + containerHeight) / ITEM_HEIGHT) + 5,
      documents.length
    );
    const newStartIndex = Math.max(0, Math.floor(scrollTop / ITEM_HEIGHT) - 5);

    setStartIndex(newStartIndex);
    setVisibleDocs(documents.slice(newStartIndex, endIndex));
  }, [documents, view]);

  useEffect(() => {
    if (view === 'documents' && documents.length > 0) {
      setVisibleDocs(documents.slice(0, 20));
    }
  }, [documents, view]);

  useEffect(() => {
    const container = scrollContainerRef.current;
    if (!container) return;

    container.addEventListener('scroll', handleScroll);
    return () => container.removeEventListener('scroll', handleScroll);
  }, [handleScroll]);

  return (
    <div className="w-64 h-screen flex flex-col bg-card starfield border-r border-border">
      {/* Logo */}
      <div className="p-4 flex items-center gap-2">
        <div className="w-6 h-6 rounded-full bg-gradient-to-br from-primary to-accent flex items-center justify-center">
          <span className="text-xs text-primary-foreground font-bold">S</span>
        </div>
        <span className="text-lg font-semibold text-foreground">StarChat AI</span>
      </div>

      {/* New Chat Button */}
      {view === 'chats' && (
        <div className="px-3 mb-2">
          <button
            onClick={onNewChat}
            className="w-full flex items-center justify-center gap-2 py-2.5 rounded-button bg-gradient-to-r from-primary to-accent text-primary-foreground font-medium text-sm btn-glow transition-all duration-300 hover:brightness-110"
          >
            <Plus className="w-4 h-4" />
            开启新对话
          </button>
        </div>
      )}

      {/* Content */}
      <div className="flex-1 overflow-y-auto px-2 py-2" ref={scrollContainerRef}>
        {loading ? (
          <div className="flex items-center justify-center py-8 text-muted-foreground text-sm">
            加载中...
          </div>
        ) : view === 'chats' ? (
          <div className="space-y-4">
            {Object.entries(conversationGroups).length === 0 ? (
              <div className="text-center py-8 text-muted-foreground text-sm">
                暂无对话记录
              </div>
            ) : (
              Object.entries(conversationGroups).map(([group, convs]) => (
                <div key={group}>
                  <p className="px-2 text-xs font-medium text-muted-foreground mb-1.5">{group}</p>
                  <div className="space-y-0.5">
                    {convs.map((conv) => (
                      <button
                        key={conv.sessionId}
                        onClick={() => onSelectChat(conv.sessionId)}
                        className={`w-full text-left px-3 py-2 rounded-lg text-sm transition-all duration-200 flex items-center gap-2 group ${activeChat === conv.sessionId
                          ? "bg-secondary border-l-2 border-primary text-foreground"
                          : "text-muted-foreground hover:bg-secondary/50 hover:text-foreground"
                          }`}
                      >
                        <MessageSquare className="w-3.5 h-3.5 shrink-0 opacity-50" />
                        <span className="truncate flex-1">{conv.title}</span>
                        <button
                          onClick={(e) => handleDeleteConversation(conv.sessionId, e)}
                          className="opacity-0 group-hover:opacity-100 p-1 hover:text-destructive transition-all"
                        >
                          <Trash2 className="w-3 h-3" />
                        </button>
                      </button>
                    ))}
                  </div>
                </div>
              ))
            )}
          </div>
        ) : (
          <div className="space-y-2 relative">
            {documents.length === 0 ? (
              <div className="text-center py-8 text-muted-foreground text-sm">
                暂无文档
              </div>
            ) : (
              <div style={{ height: `${documents.length * ITEM_HEIGHT}px` }}>
                {visibleDocs.map((doc, index) => {
                  const actualIndex = startIndex + index;
                  return (
                    <button
                      key={doc.id}
                      onClick={() => onSelectDocument?.(doc)}
                      className="absolute w-full text-left px-3 py-2 rounded-lg text-sm transition-all duration-200 flex items-center gap-2 group hover:bg-secondary/50 hover:text-foreground text-muted-foreground"
                      style={{ top: `${actualIndex * ITEM_HEIGHT}px` }}
                    >
                      <FileText className="w-3.5 h-3.5 shrink-0 opacity-50" />
                      <div className="flex-1 min-w-0">
                        <div className="truncate">{doc.fileName}</div>
                        <div className="text-xs text-muted-foreground mt-0.5">
                          {formatFileSize(doc.fileSize)} · {formatTime(doc.uploadTime)}
                        </div>
                      </div>
                      <button
                        onClick={(e) => handleDeleteDocument(doc.id, e)}
                        className="opacity-0 group-hover:opacity-100 p-1 hover:text-destructive transition-all shrink-0"
                      >
                        <Trash2 className="w-3 h-3" />
                      </button>
                    </button>
                  );
                })}
              </div>
            )}
          </div>
        )}
      </div>

      {/* Bottom Settings */}
      <div className="p-3 border-t border-border">
        <button
          onClick={() => setView(view === 'chats' ? 'documents' : 'chats')}
          className="w-full flex items-center gap-2 px-3 py-2 rounded-lg text-sm text-muted-foreground hover:bg-secondary/50 hover:text-foreground transition-colors"
        >
          <Settings className="w-4 h-4" />
          {view === 'chats' ? '文档管理' : '对话列表'}
        </button>
      </div>
    </div>
  );
});

Sidebar.displayName = 'Sidebar';

export default Sidebar;
