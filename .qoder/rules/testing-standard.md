---
trigger: always_on
---
# 测试规范

## 测试分层

### 测试金字塔

```
        /  E2E  \           <- 少量，关键流程
       / 集成测试 \          <- 适量，API 端到端
      /  单元测试  \         <- 大量，业务逻辑
```

### 覆盖率要求

| 层次 | 目标 | 最低要求 |
|------|------|----------|
| Service 单元测试 | >= 80% | >= 70% |
| Controller 集成测试 | 核心流程 100% | >= 60% |
| 前端组件测试 | >= 70% | >= 50% |

## 后端测试规范

### 技术栈

- JUnit 5：测试框架
- Mockito：Mock 框架
- MockMvc：API 集成测试
- H2：内存数据库（集成测试）
- Spring Boot Test：测试支持

### 测试类命名

```
<Entity>ServiceTest.java       # Service 单元测试
<Entity>ControllerTest.java    # Controller 集成测试
```

### 测试方法命名

```
方法名_场景_预期结果

例：
login_withValidCredentials_returnsToken()
login_withWrongPassword_throwsException()
transfer_withInsufficientBalance_throwsException()
```

### 单元测试模板

```java
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserServiceImpl userService;

    @Test
    @DisplayName("登录 - 有效凭证返回Token")
    void login_withValidCredentials_returnsToken() {
        // Given
        LoginRequest request = new LoginRequest("13800138000", "Password@123");
        User user = new User();
        user.setPhone("13800138000");
        user.setPassword("$2a$10$..."); // BCrypt hash
        when(userRepository.findByPhone("13800138000")).thenReturn(Optional.of(user));

        // When
        LoginResponse response = userService.login(request);

        // Then
        assertThat(response.getToken()).isNotNull();
    }
}
```

### 集成测试模板

```java
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("POST /api/v1/auth/login - 有效凭证返回200")
    void login_withValidCredentials_returns200() throws Exception {
        LoginRequest request = new LoginRequest("13800138000", "Password@123");

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.token").exists());
    }
}
```

## 前端测试规范

### 技术栈

- Vitest：测试框架
- Vue Test Utils：组件测试
- happy-dom：DOM 环境

### 测试文件命名

```
ComponentName.spec.ts
```

### 组件测试模板

```typescript
import { mount } from '@vue/test-utils'
import { describe, test, expect } from 'vitest'
import LoginForm from '@/components/LoginForm.vue'

describe('LoginForm', () => {
  test('渲染登录表单', () => {
    const wrapper = mount(LoginForm)
    expect(wrapper.find('form').exists()).toBe(true)
  })

  test('输入手机号和密码后提交', async () => {
    const wrapper = mount(LoginForm)
    await wrapper.find('input[name="phone"]').setValue('13800138000')
    await wrapper.find('input[name="password"]').setValue('Password@123')
    await wrapper.find('form').trigger('submit')
    // 断言
  })
})
```

## 测试场景覆盖

### 每个功能必须覆盖

1. **Happy Path**：正常流程
2. **参数校验**：非法参数
3. **边界条件**：空值、最大值、最小值
4. **异常场景**：业务异常（余额不足等）
5. **安全场景**：未认证、越权

### 金融业务测试重点

- 金额精度：使用 BigDecimal，不使用 float/double
- 并发安全：余额更新原子性
- 幂等性：重复请求不重复扣款
- 事务回滚：异常时数据一致性
