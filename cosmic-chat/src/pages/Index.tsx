import { useState, useEffect, useCallback, useRef } from "react";
import Sidebar from "@/components/Sidebar";
import ChatArea from "@/components/ChatArea";
import DocumentViewer from "@/components/DocumentViewer";
import type { Message } from "@/components/MessageBubble";
import { api, type Document } from "@/lib/api";

const Index = () => {
  const [activeChat, setActiveChat] = useState<string | null>(null);
  const [messages, setMessages] = useState<Message[]>([]);
  const [selectedDocument, setSelectedDocument] = useState<Document | null>(null);
  const [loading, setLoading] = useState(false);
  const sidebarRef = useRef<{ refreshDocuments: () => void }>(null);

  const loadConversationMessages = async () => {
    if (!activeChat) {
      setMessages([]);
      return;
    }

    try {
      setLoading(true);
      console.log('[开始加载对话消息]', activeChat);
      const messages = await api.conversations.getMessages(activeChat);
      console.log('[对话消息]', messages);

      if (!messages || messages.length === 0) {
        console.warn('[对话消息为空]', messages);
        setMessages([]);
        return;
      }

      const formattedMessages: Message[] = messages.map(msg => ({
        id: msg.id.toString(),
        role: msg.role === 'user' ? 'user' : 'ai',
        content: msg.content,
        time: new Date(msg.createTime).toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' }),
      }));
      setMessages(formattedMessages);
      console.log('[加载对话消息成功]', formattedMessages.length);
    } catch (error) {
      console.error('[加载对话消息失败]', error);
      setMessages([]);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadConversationMessages();
  }, [activeChat]);

  const handleNewChat = () => {
    setActiveChat(null);
    setMessages([]);
  };

  const handleSelectDocument = (document: Document) => {
    setSelectedDocument(document);
  };

  const handleCloseDocumentViewer = () => {
    setSelectedDocument(null);
  };

  const handleUploadSuccess = useCallback(() => {
    console.log('[文件上传成功，刷新文档列表]');
    sidebarRef.current?.refreshDocuments();
  }, []);

  const handleSessionCreated = useCallback((sessionId: string) => {
    console.log('[会话已创建]', sessionId);
    setActiveChat(sessionId);
  }, []);

  return (
    <div className="flex h-screen overflow-hidden">
      <Sidebar
        ref={sidebarRef}
        activeChat={activeChat || ""}
        onSelectChat={setActiveChat}
        onNewChat={handleNewChat}
        onSelectDocument={handleSelectDocument}
      />
      <ChatArea
        messages={messages}
        sessionId={activeChat}
        onMessagesUpdate={setMessages}
        onUploadSuccess={handleUploadSuccess}
        onSessionCreated={handleSessionCreated}
      />
      {selectedDocument && (
        <DocumentViewer
          document={selectedDocument}
          onClose={handleCloseDocumentViewer}
        />
      )}
    </div>
  );
};

export default Index;
