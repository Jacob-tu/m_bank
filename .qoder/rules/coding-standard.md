---
trigger: always_on
---
# 编码规范 - Java & Vue3

## Java 编码规范

### 命名规范

| 类型 | 规范 | 示例 |
|------|------|------|
| 类名 | UpperCamelCase | UserController, BankCardService |
| 方法名 | lowerCamelCase | findByPhone, transferMoney |
| 变量名 | lowerCamelCase | userId, cardNumber |
| 常量名 | UPPER_SNAKE_CASE | MAX_TRANSFER_AMOUNT |
| 包名 | 全小写 | com.mbank.controller |
| Entity | 名词，无后缀 | User, BankCard, Transaction |
| Repository | Entity + Repository | UserRepository |
| Service | Entity + Service | UserService |
| ServiceImpl | Entity + ServiceImpl | UserServiceImpl |
| Controller | Entity + Controller | UserController |
| DTO | 动作 + Request/Response | LoginRequest, UserResponse |

### 代码格式

- 缩进：4 个空格
- 行宽：不超过 120 字符
- 大括号：同行开括号（K&R 风格）
- 空行：方法之间一个空行，逻辑块之间一个空行

### 注释规范

```java
/**
 * 用户服务实现类
 * 处理用户注册、登录、密码管理等业务逻辑
 */
@Service
public class UserServiceImpl implements UserService {

    /**
     * 用户登录
     * @param request 登录请求（手机号+密码）
     * @return JWT Token
     * @throws BusinessException 手机号未注册或密码错误
     */
    @Override
    public LoginResponse login(LoginRequest request) {
        // 1. 根据手机号查询用户
        // 2. 验证密码
        // 3. 生成 JWT Token
        // 4. 记录审计日志
    }
}
```

### 异常处理

- 使用自定义 BusinessException 封装业务异常
- 全局异常处理器统一处理
- 不允许吞掉异常（空 catch 块）
- 资金操作异常必须记录审计日志

```java
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException e) {
        return ResponseEntity.status(e.getCode())
            .body(ApiResponse.error(e.getCode(), e.getMessage()));
    }
}
```

### 事务管理

- Service 层方法使用 @Transactional
- 只读操作标注 @Transactional(readOnly = true)
- 资金操作必须使用事务确保原子性

---

## Vue3 编码规范

### 命名规范

| 类型 | 规范 | 示例 |
|------|------|------|
| 组件文件名 | PascalCase | LoginView.vue, CardItem.vue |
| 组件名 | PascalCase | LoginView, CardItem |
| composable | use + 功能 | useAuth, useCard |
| Store | 功能 + Store | useUserStore, useCardStore |
| 事件名 | lowerCamelCase | onSubmit, onCardClick |
| Props | lowerCamelCase | cardInfo, isVisible |

### 组件结构

```vue
<script setup>
// 1. 导入
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'

// 2. Props & Emits
const props = defineProps({})
const emit = defineEmits([])

// 3. 响应式状态
const loading = ref(false)

// 4. 计算属性
const formattedAmount = computed(() => {})

// 5. 方法
async function handleSubmit() {}

// 6. 生命周期
onMounted(() => {})
</script>

<template>
  <!-- Vant4 组件 -->
</template>

<style scoped>
/* BEM 命名 */
.login-form__input {}
.login-form__button--primary {}
</style>
```

### API 调用规范

```typescript
// api/user.ts
import request from '@/utils/request'

export function login(data: LoginParams) {
  return request.post<LoginResult>('/api/auth/login', data)
}
```

### 安全规范

- Token 存储在 localStorage，请求时通过拦截器注入
- 用户输入必须校验
- 敏感信息（手机号、银行卡号）脱敏展示
- 不使用 v-html 渲染用户输入
