@Debug
Feature: Test powercenter process
  I want to test if the powercenter process steps work

  Background:
    Given the pwcSource table Customer is empty
    And the pwcTarget table Customer is empty
    When I insert the following data in pwcSource table Customer:
      | Customer_ID | Customer_Name | Country |
      |        1234 | Henk          | NL      |
      |         431 | Harry         | USA     |

  Scenario: Starting a powercenter workflow process
    When I run the demo process load_Customer
    And I retrieve the contents of the pwcTarget Customer table
    Then I expect the following result:
      | Customer_ID | Customer_Name | Country |
      |        1234 | Henk          | NL      |
      |         431 | Harry         | USA     |
      |        1234 | Henk          | NL      |
      |         431 | Harry         | USA     |
 
 Scenario: Starting a powercenter workflow process with parameters
    When I run the demoWithParameters process load_Customer
    And I retrieve the contents of the pwcTarget Customer table
    Then I expect the following result:
      | Customer_ID | Customer_Name | Country |
      |        1234 | Henkfirstsecond          | NL      |
      |         431 | Harryfirstsecond         | USA     |
      |        1234 | Henkfirstsecond          | NL      |
      |         431 | Harryfirstsecond         | USA     |
 
  Scenario: Starting a powercenter task process
    When I run the demotask process wf_m_load_Customer.s_m_load_Customer
    And I retrieve the contents of the pwcTarget Customer table
    Then I expect the following result:
      | Customer_ID | Customer_Name | Country |
      |        1234 | Henk          | NL      |
      |         431 | Harry         | USA     |
      
  Scenario: Starting a powercenter task process in a worklet
    When I run the demotask process wf_m_load_Customer.wklt_loadCustomer.s_m_load_Customer1
    And I retrieve the contents of the pwcTarget Customer table
    Then I expect the following result:
      | Customer_ID | Customer_Name | Country |
      |        1234 | Henk          | NL      |
      |         431 | Harry         | USA     |     
      
 Scenario: Starting a powercenter worklet
    When I run the demotask process wf_m_load_Customer.wklt_loadCustomer
    And I retrieve the contents of the pwcTarget Customer table
    Then I expect the following result:
      | Customer_ID | Customer_Name | Country |
      |        1234 | Henk          | NL      |
      |         431 | Harry         | USA     |     

 Scenario: Starting a powercenter task process with parameters
    When I run the demotaskWithParameters process wf_m_load_Customer.s_m_load_Customer
    And I retrieve the contents of the pwcTarget Customer table
    Then I expect the following result:
      | Customer_ID | Customer_Name | Country |
      |        1234 | Henk12          | NL      |
      |         431 | Harry12         | USA     |
      
  Scenario: Starting a powercenter task process in a worklet with parameters
    When I run the demotaskWithParameters process wf_m_load_Customer.wklt_loadCustomer.s_m_load_Customer1
    And I retrieve the contents of the pwcTarget Customer table
    Then I expect the following result:
      | Customer_ID | Customer_Name | Country |
      |        1234 | Henk34          | NL      |
      |         431 | Harry34         | USA     |     
      
 Scenario: Starting a powercenter worklet with parameters
    When I run the demotaskWithParameters process wf_m_load_Customer.wklt_loadCustomer
    And I retrieve the contents of the pwcTarget Customer table
    Then I expect the following result:
      | Customer_ID | Customer_Name | Country |
      |        1234 | Henk34          | NL      |
      |         431 | Harry34         | USA     |
      
      #I set parameter {parameter name} for process config {process config name} to {parameter value}
  Scenario: Starting a powercenter task process with overridden parameters
  	When I set parameter WFL_CustParam1 for process config demotaskWithParameters to 2
    And I run the demotaskWithParameters process wf_m_load_Customer.s_m_load_Customer
    And I retrieve the contents of the pwcTarget Customer table
    Then I expect the following result:
      | Customer_ID | Customer_Name | Country |
      |        1234 | Henk22          | NL      |
      |         431 | Harry22         | USA     |
           
           