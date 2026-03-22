# Stage 1: Build frontend
FROM node:20-alpine AS build
WORKDIR /app
COPY cosmic-chat/package*.json ./
RUN npm ci
COPY cosmic-chat/ .
RUN npm run build

# Stage 2: Serve with nginx
FROM nginx:alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/nginx.conf
