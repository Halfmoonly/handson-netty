package com.pp.netty.test;

import sun.reflect.CallerSensitive;
import sun.reflect.Reflection;

public class CallerClassTest {


    public static void main(String[] args) {
        YoungPeople youngPeople = new YoungPeople();
        youngPeople.buyHouse();
    }



   static class Parent {

       @CallerSensitive
       void buyHouse() {
           Class<?> clazz = Reflection.getCallerClass();
           System.out.println("父母为"+clazz+"买房子");
       }
    }


   static class YoungPeople {

        void buyHouse() {
            System.out.println("年轻人买房子");
            Parent parent = new Parent();
            parent.buyHouse();
        }

    }




}

