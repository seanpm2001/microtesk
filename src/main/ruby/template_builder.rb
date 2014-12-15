#
# Copyright 2013-2014 ISP RAS (http://www.ispras.ru)
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#   http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

require 'set'

require_relative 'template'
require_relative 'utils'

# TODO:
# make errors display file/line numbers (there's an example of
# that in the last version)

module TemplateBuilder

def self.define_runtime_methods(metamodel)
  modes = metamodel.getAddressingModes
  modes.each { |mode| define_addressing_mode mode }

  ops = metamodel.getOperations
  ops.each { |op| define_operation op }
end

#
# Defines methods for addressing modes (added to the Template class)
# 
def define_addressing_mode(mode)
  name = mode.getName().to_s
  #puts "Defining mode #{name}..."

  p = lambda do |*arguments|
    builder = @template.newAddressingModeBuilder name
    set_arguments builder, arguments
    builder.build
  end

  define_method_for Template, name, "mode", p
end

#
# Defines methods for operations (added to the Template class)
# 
def define_operation(op)
  name = op.getName().to_s
  is_root = op.isRoot
  root_shortcuts = op.hasRootShortcuts

  #puts "Defining operation #{name}..."

  p = lambda do |*arguments, &situations|

    builder = @template.newOperationBuilder name
    set_arguments builder, arguments

    if situations != nil
      builder.setSituation self.instance_eval &situations
    end

    if is_root
      @template.setRootOperation builder.build
      @template.endBuildingCall
    elsif root_shortcuts
      # TODO: Dirty hack! Assumes that if a root shortcut exists, we always use it. 
      builder.setContext "#root"
      @template.setRootOperation builder.build
      @template.endBuildingCall
    else
      builder
    end
  end

  define_method_for Template, name, "op", p
end

def set_arguments(builder, args)
  if args.count == 1 and args.first.is_a?(Hash)
    set_arguments_from_hash builder, args.first
  else
    set_arguments_from_array builder, args
  end
end

def set_arguments_from_hash(builder, args)
  args.each_pair do |name, value|
    value = value.to_s if value.is_a? Symbol
    builder.setArgument name.to_s, value
  end
end

def set_arguments_from_array(builder, args)
  args.each do |value|
    value = value.to_s if value.is_a? Symbol
    builder.addArgument value
  end
end

# 
# Defines a method in the target class. If such method is already defined,
# the method type is added to the method name as a prefix to make the name unique.
# If this does not help, an error is reported.  
# 
# Parameters:
#   target_class Target class (Class object)
#   method_name  Method name (String)
#   method_type  Method type (String) 
#   method_body  Body for the method (Proc)
#
def define_method_for(target_class, method_name, method_type, method_body)

  method_name = method_name.downcase
  # puts "Defining method #{target_class}.#{method_name} (#{method_type})..."

  if !target_class.method_defined?(method_name)
    target_class.send(:define_method, method_name, method_body)
  elsif !target_class.method_defined?("#{method_type}_#{method_name}")
    target_class.send(:define_method, "#{method_type}_#{method_name}", method_body)
  else
    puts "Error: Failed to define the #{method_name} method (#{method_type})"
  end

end

end # TemplateBuilder
