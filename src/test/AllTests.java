package test;

public class AllTests {
    public static void main(String[] args) throws Exception {
        CoreGraphTest.main(args);
        GraphTest.main(args);
        ConfigTest.main(args);
        HTTPServerTest.main(args);
        WebAppTest.main(args);
        System.out.println("AllTests passed");
    }
}