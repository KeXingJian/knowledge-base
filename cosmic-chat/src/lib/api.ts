const API_BASE_URL = '/api';

interface ApiResponse<T> {
  code: number;
  message: string;
  data: T;
}

export interface Message {
  id: number;
  role: 'user' | 'assistant';
  content: string;
  createTime: string;
}

export interface Conversation {
  id: number;
  sessionId: string;
  title: string;
  createTime: string;
  updateTime: string;
  messageCount: number;
  messages?: Message[];
}

export interface ConversationDetail {
  id: number;
  sessionId: string;
  title: string;
  createTime: string;
  updateTime: string;
  messageCount: number;
  messages: Message[];
}

export interface ChatResponse {
  answer: string;
  sessionId: string;
}

export interface Document {
  id: number;
  fileName: string;
  fileType: string;
  filePath: string;
  fileSize: number;
  fileHash: string;
  chunkCount: number;
  uploadTime: string;
  updateTime: string;
  processed: boolean;
  errorMessage: string | null;
  content?: string;
}

export interface BatchUploadProgress {
  taskId: string;
  totalDocuments: number;
  completedDocuments: number;
  failedDocuments: number;
  progress: number;
  status: 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED';
}

const request = async <T>(url: string, options?: RequestInit): Promise<T> => {
  const response = await fetch(`${API_BASE_URL}${url}`, {
    headers: {
      'Content-Type': 'application/json',
      ...options?.headers,
    },
    ...options,
  });

  if (!response.ok) {
    throw new Error(`HTTP error! status: ${response.status}`);
  }

  const result: ApiResponse<T> = await response.json();

  if (result.code !== 200) {
    throw new Error(result.message || '请求失败');
  }

  return result.data;
};

export const api = {
  conversations: {
    chat: (sessionId: string, question: string) =>
      request<ChatResponse>('/conversations/chat', {
        method: 'POST',
        body: JSON.stringify({ sessionId, question }),
      }),

    getDetail: (sessionId: string) =>
      request<ConversationDetail>(`/conversations/${sessionId}`),

    getAll: () =>
      request<Conversation[]>('/conversations/list'),

    getMessages: (sessionId: string) =>
      request<Message[]>(`/conversations/messages/${sessionId}`),

    delete: (sessionId: string) =>
      request<string>(`/conversations/delete/${sessionId}`, {
        method: 'DELETE',
      }),
  },

  documents: {
    batchUpload: async (files: File[]): Promise<string> => {
      const formData = new FormData();
      files.forEach(file => formData.append('files', file));

      console.log('[发送上传请求]', files.map(f => f.name));

      const response = await fetch(`${API_BASE_URL}/documents/batch-upload`, {
        method: 'POST',
        body: formData,
      });

      const result: ApiResponse<string> = await response.json();
      console.log('[上传响应]', JSON.stringify(result, null, 2));

      if (result.code !== 200) {
        throw new Error(result.message || '上传失败');
      }

      console.log('[上传返回任务ID]', result.data);
      return result.data;
    },

    getUploadProgress: (taskId: string) =>
      request<BatchUploadProgress>(`/documents/batch-upload/progress/${taskId}`),

    getList: () =>
      request<Document[]>('/documents/list'),

    getDetail: (id: number) =>
      request<string>(`/documents/${id}`),

    delete: (id: number) =>
      request<string>(`/documents/${id}`, {
        method: 'DELETE',
      }),
  },
};
