package domain;

import com.example.domain.DaoFactory;
import com.example.domain.Level;
import com.example.domain.User;
import com.example.domain.UserDao;
import com.example.service.UserServiceImpl;
import com.example.service.UserServiceTx;
import com.example.service.UsualUpgradePolicy;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.mail.MailException;
import org.springframework.mail.MailSender;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.transaction.PlatformTransactionManager;


import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class UserServiceImplTest {
    UserServiceImpl userServiceImpl;
    static ApplicationContext context;
    List<User> users = new ArrayList<>();

    PlatformTransactionManager transactionManager;
    UserDao userDao;
    DataSource dataSource ;

    static class TestUserServiceImpl extends UserServiceImpl {
        private int id;
        public TestUserServiceImpl(int id){
            this.id = id;
        }
        @Override
        protected void upgradeLevel(User user){
            if(user.getId() == this.id) throw new UpgradeException();
            super.upgradeLevel(user);
        }
    }


    static class TestSender implements MailSender {
        List<String> requests = new ArrayList<>();
        @Override
        public void send(SimpleMailMessage... simpleMessages) throws MailException {
        }
        @Override
        public void send(SimpleMailMessage simpleMessage) throws MailException {
            SimpleMailMessage message = simpleMessage;
            String request = message.getTo()[0];
            requests.add(request);
        }

        public List<String> getRequests() {
            return requests;
        }


    }
    static class UpgradeException extends RuntimeException{
    }
    @BeforeAll
    public static void setUpContext() {
        context = new AnnotationConfigApplicationContext(DaoFactory.class);
    }

    @BeforeEach
    void setUp() {
        userServiceImpl = context.getBean(UserServiceImpl.class);
        userDao = context.getBean(UserDao.class);
        dataSource = context.getBean(DataSource.class);
        transactionManager = context.getBean(PlatformTransactionManager.class);
//        userServiceImpl.setMailSender(new ());
        users = Arrays.asList(
                new User(1, "username1", "password1", Level.BASIC, 49, 0, "username1@gmail.com"),
                new User(2, "username2", "password2", Level.BASIC, 50, 0,"username2@gmail.com"),
                new User(3, "username3", "password2", Level.SILVER, 50, 20,"username3@gmail.com"),
                new User(4, "username4", "password2", Level.SILVER, 50, 30,"username4@gmail.com"),
                new User(5, "username5", "password3", Level.GOLD, 20, 30,"username5@gmail.com")
        );

    }

    @Test
    void isthesameInstace() {
        System.out.println(this);
        System.out.println(userServiceImpl);
        System.out.println(context);
    }

    @Test
    void isthesameInstace2() {
        System.out.println(this);
        System.out.println(userServiceImpl);
        System.out.println(context);

    }

    @Test
    void upgradeLevels() throws Exception {
        userDao.deleteAll();
        for ( User user : users ) {
            userDao.add(user);
        }
        userServiceImpl.upgradeLevels();
        checkLevel(users.get(0),false);
        checkLevel(users.get(1),true);
        checkLevel(users.get(2),false);
        checkLevel(users.get(3),true);
        checkLevel(users.get(4),false);

        TestSender testSender = (TestSender) userServiceImpl.getMailSender();
        List<String> emails = testSender.getRequests();
        System.out.println(users.get(1).getEmail() + emails.get(0));


        String req_email = testSender.getRequests().get(0);

        assert users.get(1).getEmail().equals(emails.get(0));
        assert users.get(3).getEmail().equals(emails.get(1));



    }
    @Test
    void upgradeAllOrNothing() throws  Exception {
        //Arrange
        UserServiceImpl testUserServiceImpl = new TestUserServiceImpl(users.get(3).getId());
        testUserServiceImpl.setUserDao(this.userDao);
        testUserServiceImpl.setUpgradePolicy(new UsualUpgradePolicy());
        testUserServiceImpl.setMailSender(new TestSender());

        UserServiceTx userServiceTx = new UserServiceTx(transactionManager,testUserServiceImpl);
        userDao.deleteAll();
        for(User user : users){
            userDao.add(user);
        }

        try {
            userServiceTx.upgradeLevels();//ACT
            throw new RuntimeException("No Exception thrown");
        } catch(UpgradeException e) {
            System.out.println("Upgrade Exception !");
        }
        checkLevel(users.get(0),false);
        checkLevel(users.get(1),false);
        checkLevel(users.get(2),false);
        checkLevel(users.get(3),false);
        checkLevel(users.get(4),false);

        TestSender testSender = (TestSender) testUserServiceImpl.getMailSender();
        List<String> emails = testSender.getRequests();
//        assert emails.size() == 0;


    }
    private void checkLevel(User user, boolean upgrade) {
        User updatedUser = userDao.get(user.getUsername());
        if(upgrade){
            assert updatedUser.getLevel() ==user.getLevel().nextLevel();
        } else{
            assert updatedUser.getLevel() ==user.getLevel();
        }

    }


    @Test
    void sendMail(){
        userServiceImpl.sendUpgradeEmail(users.get(0));
        TestSender testSender=  (TestSender) userServiceImpl.getMailSender();
        System.out.println(testSender.getRequests().get(0));
        System.out.println(users.get(0).getEmail());
        String req_email = testSender.getRequests().get(0);
        assert req_email.equals(users.get(0).getEmail());
    }


    @Test void add(){
        userDao.deleteAll();
        User userWithoutLevel = users.get(0);
        User userWithLevel = users.get(4);
        userWithoutLevel.setLevel(null);
        userServiceImpl.add(userWithoutLevel);
        userServiceImpl.add(userWithLevel);
        User userWithoutLevelRead = userDao.get(userWithoutLevel.getUsername());
        User userWithLevelRead =  userDao.get(userWithLevel.getUsername());
        assert userWithoutLevelRead.getLevel() == Level.BASIC;
        assert userWithLevel.getLevel() == userWithLevelRead.getLevel();
    }
}
